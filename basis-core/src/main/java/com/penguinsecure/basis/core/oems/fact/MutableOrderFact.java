package com.penguinsecure.basis.core.oems.fact;

import com.penguinsecure.basis.core.oems.ChildOrderState;

/** Caller-owned primitive order fact shared by simulated and real venue gateways. */
public final class MutableOrderFact {
    private OrderFactType type;
    private OrderFactProvenance provenance;
    private long localOrderIdHigh;
    private long localOrderIdLow;
    private int venueId;
    private int instrumentId;
    private long sessionGeneration;
    private long receiveEpochNanos;
    private long receiveMonoNanos;
    private long executionIdentityHash;
    private long fillQuantity;
    private long fillPriceTicks;
    private long authoritativeFilled;
    private ChildOrderState authoritativeState;
    private int reasonCode;

    public MutableOrderFact reset() {
        type = null;
        provenance = null;
        localOrderIdHigh = 0;
        localOrderIdLow = 0;
        venueId = 0;
        instrumentId = 0;
        sessionGeneration = 0;
        receiveEpochNanos = 0;
        receiveMonoNanos = 0;
        executionIdentityHash = 0;
        fillQuantity = 0;
        fillPriceTicks = 0;
        authoritativeFilled = 0;
        authoritativeState = null;
        reasonCode = 0;
        return this;
    }

    @SuppressWarnings("ParameterNumber")
    public MutableOrderFact set(
            final OrderFactType newType,
            final OrderFactProvenance newProvenance,
            final long newLocalOrderIdHigh,
            final long newLocalOrderIdLow,
            final int newVenueId,
            final int newInstrumentId,
            final long newSessionGeneration,
            final long newReceiveEpochNanos,
            final long newReceiveMonoNanos,
            final long newExecutionIdentityHash,
            final long newFillQuantity,
            final long newFillPriceTicks,
            final long newAuthoritativeFilled,
            final ChildOrderState newAuthoritativeState,
            final int newReasonCode) {
        type = newType;
        provenance = newProvenance;
        localOrderIdHigh = newLocalOrderIdHigh;
        localOrderIdLow = newLocalOrderIdLow;
        venueId = newVenueId;
        instrumentId = newInstrumentId;
        sessionGeneration = newSessionGeneration;
        receiveEpochNanos = newReceiveEpochNanos;
        receiveMonoNanos = newReceiveMonoNanos;
        executionIdentityHash = newExecutionIdentityHash;
        fillQuantity = newFillQuantity;
        fillPriceTicks = newFillPriceTicks;
        authoritativeFilled = newAuthoritativeFilled;
        authoritativeState = newAuthoritativeState;
        reasonCode = newReasonCode;
        return this;
    }

    public boolean isComplete() {
        if (type == null
                || provenance == null
                || (localOrderIdHigh == 0 && localOrderIdLow == 0)
                || venueId <= 0
                || instrumentId <= 0
                || sessionGeneration <= 0
                || receiveEpochNanos <= 0
                || receiveMonoNanos <= 0) return false;
        if (type == OrderFactType.FILL) {
            return executionIdentityHash != 0 && fillQuantity > 0 && fillPriceTicks > 0;
        }
        return type != OrderFactType.RECONCILED
                || (authoritativeState != null && authoritativeFilled >= 0);
    }

    public OrderFactType type() {
        return type;
    }

    public OrderFactProvenance provenance() {
        return provenance;
    }

    public long localOrderIdHigh() {
        return localOrderIdHigh;
    }

    public long localOrderIdLow() {
        return localOrderIdLow;
    }

    public int venueId() {
        return venueId;
    }

    public int instrumentId() {
        return instrumentId;
    }

    public long sessionGeneration() {
        return sessionGeneration;
    }

    public long receiveEpochNanos() {
        return receiveEpochNanos;
    }

    public long receiveMonoNanos() {
        return receiveMonoNanos;
    }

    public long executionIdentityHash() {
        return executionIdentityHash;
    }

    public long fillQuantity() {
        return fillQuantity;
    }

    public long fillPriceTicks() {
        return fillPriceTicks;
    }

    public long authoritativeFilled() {
        return authoritativeFilled;
    }

    public ChildOrderState authoritativeState() {
        return authoritativeState;
    }

    public int reasonCode() {
        return reasonCode;
    }
}
