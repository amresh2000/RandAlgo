package com.penguinsecure.basis.core.command;

/** Reusable command view populated while draining the command lane. */
public final class MutableOrderCommand {
    private OrderCommandType type;
    private OrderUrgency urgency;
    private long localOrderIdHigh;
    private long localOrderIdLow;
    private int venueId;
    private int instrumentId;
    private OrderSide side;
    private long quantity;
    private long limitPriceTicks;
    private long createdMonoNanos;

    @SuppressWarnings("ParameterNumber")
    public MutableOrderCommand set(
            final OrderCommandType newType,
            final OrderUrgency newUrgency,
            final long newLocalOrderIdHigh,
            final long newLocalOrderIdLow,
            final int newVenueId,
            final int newInstrumentId,
            final OrderSide newSide,
            final long newQuantity,
            final long newLimitPriceTicks,
            final long newCreatedMonoNanos) {
        type = newType;
        urgency = newUrgency;
        localOrderIdHigh = newLocalOrderIdHigh;
        localOrderIdLow = newLocalOrderIdLow;
        venueId = newVenueId;
        instrumentId = newInstrumentId;
        side = newSide;
        quantity = newQuantity;
        limitPriceTicks = newLimitPriceTicks;
        createdMonoNanos = newCreatedMonoNanos;
        return this;
    }

    public OrderCommandType type() {
        return type;
    }

    public OrderUrgency urgency() {
        return urgency;
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

    public OrderSide side() {
        return side;
    }

    public long quantity() {
        return quantity;
    }

    public long limitPriceTicks() {
        return limitPriceTicks;
    }

    public long createdMonoNanos() {
        return createdMonoNanos;
    }
}
