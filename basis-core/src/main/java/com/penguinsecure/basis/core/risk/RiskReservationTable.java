package com.penguinsecure.basis.core.risk;

import com.penguinsecure.basis.core.numeric.NumericStatus;

/** Fixed-capacity generation-fenced reservation table. */
public final class RiskReservationTable {
    private final StrategyRiskLedger ledger;
    private final int[] generations;
    private final int[] nextFree;
    private final int[] strategySlots;
    private final long[] configurationGenerations;
    private final long[] remainingGross;
    private final long[] reservedNetExposure;
    private final long[] reservedUnhedgedExposure;
    private final long[] collateral;
    private final long[] remainingHedgeClaims;
    private final long[] expiryMonoNanos;
    private final RiskReservationState[] states;
    private final PartitionedTokenBucket[] rateBuckets;
    private int freeHead;

    public RiskReservationTable(final int capacity, final StrategyRiskLedger ledger) {
        if (capacity <= 0 || ledger == null) throw new IllegalArgumentException("invalid table");
        this.ledger = ledger;
        generations = new int[capacity];
        nextFree = new int[capacity];
        strategySlots = new int[capacity];
        configurationGenerations = new long[capacity];
        remainingGross = new long[capacity];
        reservedNetExposure = new long[capacity];
        reservedUnhedgedExposure = new long[capacity];
        collateral = new long[capacity];
        remainingHedgeClaims = new long[capacity];
        expiryMonoNanos = new long[capacity];
        states = new RiskReservationState[capacity];
        rateBuckets = new PartitionedTokenBucket[capacity];
        for (int slot = 0; slot < capacity; slot++) {
            nextFree[slot] = slot + 1;
            states[slot] = RiskReservationState.FREE;
        }
        nextFree[capacity - 1] = -1;
    }

    @SuppressWarnings("ParameterNumber")
    public RiskReservationStatus reserve(
            final int strategySlot,
            final long configurationGeneration,
            final long gross,
            final long netExposure,
            final long unhedgedExposure,
            final long reservedCollateral,
            final long hedgeClaims,
            final long expiry,
            final PartitionedTokenBucket bucket,
            final MutableRiskReservationHandle destination) {
        if (gross <= 0
                || reservedCollateral < 0
                || unhedgedExposure < 0
                || hedgeClaims < 0
                || expiry <= 0
                || bucket == null
                || destination == null
                || !ledger.matchesGeneration(strategySlot, configurationGeneration)) {
            return RiskReservationStatus.INVALID_STATE;
        }
        if (freeHead < 0) return RiskReservationStatus.CAPACITY_EXHAUSTED;
        final NumericStatus ledgerStatus =
                ledger.reserve(
                        strategySlot, gross, netExposure, unhedgedExposure, reservedCollateral);
        if (ledgerStatus != NumericStatus.OK) return RiskReservationStatus.NUMERIC_FAILURE;
        final int slot = freeHead;
        freeHead = nextFree[slot];
        int generation = generations[slot] + 1;
        if (generation == 0) generation = 1;
        generations[slot] = generation;
        strategySlots[slot] = strategySlot;
        configurationGenerations[slot] = configurationGeneration;
        remainingGross[slot] = gross;
        reservedNetExposure[slot] = netExposure;
        reservedUnhedgedExposure[slot] = unhedgedExposure;
        collateral[slot] = reservedCollateral;
        remainingHedgeClaims[slot] = hedgeClaims;
        expiryMonoNanos[slot] = expiry;
        rateBuckets[slot] = bucket;
        states[slot] = RiskReservationState.RESERVED;
        destination.set(slot, generation);
        return RiskReservationStatus.OK;
    }

    public RiskReservationStatus markSent(final int slot, final int generation) {
        if (!valid(slot, generation)) return RiskReservationStatus.INVALID_HANDLE;
        if (states[slot] == RiskReservationState.SENT) return RiskReservationStatus.OK;
        if (states[slot] != RiskReservationState.RESERVED)
            return RiskReservationStatus.INVALID_STATE;
        states[slot] = RiskReservationState.SENT;
        return RiskReservationStatus.OK;
    }

    public RiskReservationStatus consumeFill(
            final int slot, final int generation, final long grossFill, final long signedNetDelta) {
        if (!valid(slot, generation)) return RiskReservationStatus.INVALID_HANDLE;
        if (states[slot] == RiskReservationState.FREE
                || grossFill < 0
                || grossFill > remainingGross[slot]) return RiskReservationStatus.INVALID_STATE;
        if (ledger.consume(strategySlots[slot], grossFill, signedNetDelta) != NumericStatus.OK) {
            return RiskReservationStatus.NUMERIC_FAILURE;
        }
        remainingGross[slot] -= grossFill;
        return RiskReservationStatus.OK;
    }

    public boolean consumeHedgeClaim(final int slot, final int generation) {
        if (!valid(slot, generation) || remainingHedgeClaims[slot] <= 0) return false;
        if (!rateBuckets[slot].consumeReservedHedge()) return false;
        remainingHedgeClaims[slot]--;
        return true;
    }

    public void refundHedgeClaim(final int slot, final int generation) {
        if (!valid(slot, generation)) throw new IllegalArgumentException("invalid reservation");
        rateBuckets[slot].refundConsumedHedge();
        remainingHedgeClaims[slot]++;
    }

    public boolean consumeEmergencyClaim(
            final int slot, final int generation, final long nowMonoNanos) {
        return valid(slot, generation) && rateBuckets[slot].tryConsumeEmergency(nowMonoNanos);
    }

    public void refundEmergencyClaim(final int slot, final int generation) {
        if (!valid(slot, generation)) throw new IllegalArgumentException("invalid reservation");
        rateBuckets[slot].refundEmergency();
    }

    public RiskReservationStatus markUnknown(final int slot, final int generation) {
        if (!valid(slot, generation)) return RiskReservationStatus.INVALID_HANDLE;
        if (states[slot] == RiskReservationState.UNKNOWN) return RiskReservationStatus.OK;
        if (states[slot] != RiskReservationState.SENT) return RiskReservationStatus.INVALID_STATE;
        states[slot] = RiskReservationState.UNKNOWN;
        ledger.markUnknown(strategySlots[slot]);
        return RiskReservationStatus.OK;
    }

    public RiskReservationStatus beginReconciliation(final int slot, final int generation) {
        if (!valid(slot, generation)) return RiskReservationStatus.INVALID_HANDLE;
        if (states[slot] != RiskReservationState.UNKNOWN)
            return RiskReservationStatus.INVALID_STATE;
        states[slot] = RiskReservationState.RECONCILING;
        return RiskReservationStatus.OK;
    }

    public RiskReservationStatus releaseAuthoritatively(final int slot, final int generation) {
        if (!valid(slot, generation)) return RiskReservationStatus.INVALID_HANDLE;
        final boolean unknown =
                states[slot] == RiskReservationState.UNKNOWN
                        || states[slot] == RiskReservationState.RECONCILING;
        if (states[slot] == RiskReservationState.RESERVED) {
            if (remainingHedgeClaims[slot] > 0) {
                rateBuckets[slot].cancelInitiationReservation(remainingHedgeClaims[slot]);
            } else {
                rateBuckets[slot].refundEmergency();
            }
        } else {
            rateBuckets[slot].releaseReservedHedge(remainingHedgeClaims[slot]);
        }
        ledger.release(
                strategySlots[slot],
                remainingGross[slot],
                reservedNetExposure[slot],
                reservedUnhedgedExposure[slot],
                collateral[slot],
                unknown);
        free(slot);
        return RiskReservationStatus.OK;
    }

    public boolean expireUnsent(final int slot, final int generation, final long nowMonoNanos) {
        if (!valid(slot, generation)
                || states[slot] != RiskReservationState.RESERVED
                || nowMonoNanos < expiryMonoNanos[slot]) return false;
        releaseAuthoritatively(slot, generation);
        return true;
    }

    public RiskReservationState state(final int slot, final int generation) {
        return valid(slot, generation) ? states[slot] : RiskReservationState.FREE;
    }

    public long remainingGross(final int slot, final int generation) {
        return valid(slot, generation) ? remainingGross[slot] : 0;
    }

    public long remainingHedgeClaims(final int slot, final int generation) {
        return valid(slot, generation) ? remainingHedgeClaims[slot] : 0;
    }

    private boolean valid(final int slot, final int generation) {
        return slot >= 0
                && slot < states.length
                && generation != 0
                && generations[slot] == generation
                && states[slot] != RiskReservationState.FREE;
    }

    private void free(final int slot) {
        states[slot] = RiskReservationState.FREE;
        strategySlots[slot] = 0;
        configurationGenerations[slot] = 0;
        remainingGross[slot] = 0;
        reservedNetExposure[slot] = 0;
        reservedUnhedgedExposure[slot] = 0;
        collateral[slot] = 0;
        remainingHedgeClaims[slot] = 0;
        expiryMonoNanos[slot] = 0;
        rateBuckets[slot] = null;
        nextFree[slot] = freeHead;
        freeHead = slot;
    }
}
