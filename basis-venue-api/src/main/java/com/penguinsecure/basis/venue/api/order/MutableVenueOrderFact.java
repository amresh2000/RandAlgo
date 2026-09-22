package com.penguinsecure.basis.venue.api.order;

/** Reusable venue-side fact that deliberately contains no OEMS implementation types. */
public final class MutableVenueOrderFact {
    private VenueOrderFactType type;
    private long idHigh;
    private long idLow;
    private int venueId;
    private int instrumentId;
    private long sessionGeneration;
    private long receiveEpochNanos;
    private long receiveMonoNanos;
    private long executionHash;
    private long fillQuantity;
    private long fillPrice;
    private long authoritativeFilled;
    private VenueOrderState authoritativeState;
    private int reasonCode;

    @SuppressWarnings("ParameterNumber")
    public MutableVenueOrderFact set(
            final VenueOrderFactType newType,
            final long newIdHigh,
            final long newIdLow,
            final int newVenueId,
            final int newInstrumentId,
            final long newSessionGeneration,
            final long newReceiveEpochNanos,
            final long newReceiveMonoNanos,
            final long newExecutionHash,
            final long newFillQuantity,
            final long newFillPrice,
            final long newAuthoritativeFilled,
            final VenueOrderState newAuthoritativeState,
            final int newReasonCode) {
        type = newType;
        idHigh = newIdHigh;
        idLow = newIdLow;
        venueId = newVenueId;
        instrumentId = newInstrumentId;
        sessionGeneration = newSessionGeneration;
        receiveEpochNanos = newReceiveEpochNanos;
        receiveMonoNanos = newReceiveMonoNanos;
        executionHash = newExecutionHash;
        fillQuantity = newFillQuantity;
        fillPrice = newFillPrice;
        authoritativeFilled = newAuthoritativeFilled;
        authoritativeState = newAuthoritativeState;
        reasonCode = newReasonCode;
        return this;
    }

    public VenueOrderFactType type() {
        return type;
    }

    public long idHigh() {
        return idHigh;
    }

    public long idLow() {
        return idLow;
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

    public long executionHash() {
        return executionHash;
    }

    public long fillQuantity() {
        return fillQuantity;
    }

    public long fillPrice() {
        return fillPrice;
    }

    public long authoritativeFilled() {
        return authoritativeFilled;
    }

    public VenueOrderState authoritativeState() {
        return authoritativeState;
    }

    public int reasonCode() {
        return reasonCode;
    }
}
