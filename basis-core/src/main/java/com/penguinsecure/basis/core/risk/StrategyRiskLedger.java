package com.penguinsecure.basis.core.risk;

import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;

/** Fixed strategy-generation-isolated exposure and reservation ledger. */
public final class StrategyRiskLedger {
    private final int[] strategyIds;
    private final long[] configurationGenerations;
    private final long[] confirmedGross;
    private final long[] pendingGross;
    private final long[] netExposure;
    private final long[] pendingNetExposure;
    private final long[] pendingUnhedgedExposure;
    private final long[] position;
    private final long[] reservedCollateral;
    private final long[] dailyLoss;
    private final int[] activeGroups;
    private final int[] unknownGroups;
    private final MutableLongResult arithmetic = new MutableLongResult();

    public StrategyRiskLedger(final int strategyCapacity) {
        if (strategyCapacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        strategyIds = new int[strategyCapacity];
        configurationGenerations = new long[strategyCapacity];
        confirmedGross = new long[strategyCapacity];
        pendingGross = new long[strategyCapacity];
        netExposure = new long[strategyCapacity];
        pendingNetExposure = new long[strategyCapacity];
        pendingUnhedgedExposure = new long[strategyCapacity];
        position = new long[strategyCapacity];
        reservedCollateral = new long[strategyCapacity];
        dailyLoss = new long[strategyCapacity];
        activeGroups = new int[strategyCapacity];
        unknownGroups = new int[strategyCapacity];
    }

    public void configure(
            final int slot, final int strategyId, final long configurationGeneration) {
        requireSlot(slot);
        if (strategyId <= 0 || configurationGeneration <= 0 || activeGroups[slot] != 0) {
            throw new IllegalArgumentException("invalid or active strategy slot");
        }
        strategyIds[slot] = strategyId;
        configurationGenerations[slot] = configurationGeneration;
        confirmedGross[slot] = 0;
        pendingGross[slot] = 0;
        netExposure[slot] = 0;
        pendingNetExposure[slot] = 0;
        pendingUnhedgedExposure[slot] = 0;
        position[slot] = 0;
        reservedCollateral[slot] = 0;
        dailyLoss[slot] = 0;
        unknownGroups[slot] = 0;
    }

    public boolean matches(final int slot, final int strategyId, final long generation) {
        return validSlot(slot)
                && strategyIds[slot] == strategyId
                && configurationGenerations[slot] == generation;
    }

    public boolean matchesGeneration(final int slot, final long generation) {
        return validSlot(slot) && configurationGenerations[slot] == generation;
    }

    public NumericStatus reserve(
            final int slot,
            final long grossExposure,
            final long netExposureDelta,
            final long unhedgedExposure,
            final long collateral) {
        if (!validSlot(slot)
                || grossExposure <= 0
                || unhedgedExposure < 0
                || collateral < 0
                || activeGroups[slot] == Integer.MAX_VALUE) return NumericStatus.MALFORMED;
        if (CheckedDecimalMath.add(pendingGross[slot], grossExposure, arithmetic)
                != NumericStatus.OK) return NumericStatus.OVERFLOW;
        final long pending = arithmetic.value();
        if (CheckedDecimalMath.add(pendingNetExposure[slot], netExposureDelta, arithmetic)
                != NumericStatus.OK) return NumericStatus.OVERFLOW;
        final long pendingNet = arithmetic.value();
        if (CheckedDecimalMath.add(pendingUnhedgedExposure[slot], unhedgedExposure, arithmetic)
                != NumericStatus.OK) return NumericStatus.OVERFLOW;
        final long pendingUnhedged = arithmetic.value();
        if (CheckedDecimalMath.add(reservedCollateral[slot], collateral, arithmetic)
                != NumericStatus.OK) return NumericStatus.OVERFLOW;
        pendingGross[slot] = pending;
        pendingNetExposure[slot] = pendingNet;
        pendingUnhedgedExposure[slot] = pendingUnhedged;
        reservedCollateral[slot] = arithmetic.value();
        activeGroups[slot]++;
        return NumericStatus.OK;
    }

    public NumericStatus consume(final int slot, final long grossFill, final long signedNetDelta) {
        if (!validSlot(slot) || grossFill < 0 || grossFill > pendingGross[slot]) {
            return NumericStatus.MALFORMED;
        }
        if (CheckedDecimalMath.add(confirmedGross[slot], grossFill, arithmetic) != NumericStatus.OK)
            return NumericStatus.OVERFLOW;
        final long confirmed = arithmetic.value();
        if (CheckedDecimalMath.add(netExposure[slot], signedNetDelta, arithmetic)
                != NumericStatus.OK) return NumericStatus.OVERFLOW;
        pendingGross[slot] -= grossFill;
        confirmedGross[slot] = confirmed;
        netExposure[slot] = arithmetic.value();
        position[slot] = netExposure[slot];
        return NumericStatus.OK;
    }

    public void release(
            final int slot,
            final long remainingGross,
            final long reservedNetExposure,
            final long reservedUnhedgedExposure,
            final long collateral,
            final boolean unknown) {
        requireSlot(slot);
        if (remainingGross < 0
                || remainingGross > pendingGross[slot]
                || reservedUnhedgedExposure < 0
                || reservedUnhedgedExposure > pendingUnhedgedExposure[slot]
                || collateral < 0
                || collateral > reservedCollateral[slot]
                || activeGroups[slot] <= 0) {
            throw new IllegalStateException("invalid ledger release");
        }
        pendingGross[slot] -= remainingGross;
        pendingNetExposure[slot] -= reservedNetExposure;
        pendingUnhedgedExposure[slot] -= reservedUnhedgedExposure;
        reservedCollateral[slot] -= collateral;
        activeGroups[slot]--;
        if (unknown) unknownGroups[slot]--;
    }

    public void markUnknown(final int slot) {
        requireSlot(slot);
        unknownGroups[slot]++;
    }

    public void reconcile(
            final int slot,
            final long newConfirmedGross,
            final long newNetExposure,
            final long newPosition,
            final long newDailyLoss) {
        requireSlot(slot);
        if (newConfirmedGross < 0 || newDailyLoss < 0) {
            throw new IllegalArgumentException("invalid reconciliation values");
        }
        confirmedGross[slot] = newConfirmedGross;
        netExposure[slot] = newNetExposure;
        position[slot] = newPosition;
        dailyLoss[slot] = newDailyLoss;
    }

    public long confirmedGross(final int slot) {
        return confirmedGross[slot];
    }

    public long pendingGross(final int slot) {
        return pendingGross[slot];
    }

    public long netExposure(final int slot) {
        return netExposure[slot];
    }

    public long pendingNetExposure(final int slot) {
        return pendingNetExposure[slot];
    }

    public long pendingUnhedgedExposure(final int slot) {
        return pendingUnhedgedExposure[slot];
    }

    public long position(final int slot) {
        return position[slot];
    }

    public long reservedCollateral(final int slot) {
        return reservedCollateral[slot];
    }

    public long dailyLoss(final int slot) {
        return dailyLoss[slot];
    }

    public int activeGroups(final int slot) {
        return activeGroups[slot];
    }

    public int unknownGroups(final int slot) {
        return unknownGroups[slot];
    }

    private void requireSlot(final int slot) {
        if (!validSlot(slot)) throw new IllegalArgumentException("invalid strategy slot");
    }

    private boolean validSlot(final int slot) {
        return slot >= 0 && slot < strategyIds.length;
    }
}
