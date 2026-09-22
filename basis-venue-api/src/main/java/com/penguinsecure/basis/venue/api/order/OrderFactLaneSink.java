package com.penguinsecure.basis.venue.api.order;

import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.fact.MutableOrderFact;
import com.penguinsecure.basis.core.oems.fact.OrderFactProvenance;
import com.penguinsecure.basis.core.oems.fact.OrderFactType;
import com.penguinsecure.basis.venue.api.lane.OrderFactLane;

/** Architecture boundary translating neutral venue facts onto the OEMS fact lane. */
public final class OrderFactLaneSink implements VenueOrderFactSink {
    private final OrderFactLane lane;
    private final MutableOrderFact target = new MutableOrderFact();

    public OrderFactLaneSink(final OrderFactLane lane) {
        if (lane == null) throw new NullPointerException("lane is required");
        this.lane = lane;
    }

    @Override
    public boolean publish(final MutableVenueOrderFact source) {
        if (source == null || source.type() == null) return false;
        target.set(
                factType(source.type()),
                OrderFactProvenance.ACTUAL,
                source.idHigh(),
                source.idLow(),
                source.venueId(),
                source.instrumentId(),
                source.sessionGeneration(),
                source.receiveEpochNanos(),
                source.receiveMonoNanos(),
                source.executionHash(),
                source.fillQuantity(),
                source.fillPrice(),
                source.authoritativeFilled(),
                source.authoritativeState() == null ? null : state(source.authoritativeState()),
                source.reasonCode());
        return lane.publish(target);
    }

    private static OrderFactType factType(final VenueOrderFactType type) {
        return switch (type) {
            case WRITE_ACCEPTED -> OrderFactType.WRITE_ACCEPTED;
            case WRITE_FAILED -> OrderFactType.WRITE_FAILED;
            case WRITE_AMBIGUOUS -> OrderFactType.WRITE_AMBIGUOUS;
            case RATE_LIMITED -> OrderFactType.RATE_LIMITED;
            case DISCONNECTED -> OrderFactType.DISCONNECTED;
            case ACKNOWLEDGED -> OrderFactType.ACKNOWLEDGED;
            case FILL -> OrderFactType.FILL;
            case CANCELLED -> OrderFactType.CANCELLED;
            case REJECTED -> OrderFactType.REJECTED;
            case RECONCILED -> OrderFactType.RECONCILED;
        };
    }

    private static ChildOrderState state(final VenueOrderState state) {
        return switch (state) {
            case WORKING -> ChildOrderState.WORKING;
            case PARTIALLY_FILLED -> ChildOrderState.PARTIALLY_FILLED;
            case FILLED -> ChildOrderState.FILLED;
            case CANCELLED -> ChildOrderState.CANCELLED;
            case REJECTED -> ChildOrderState.REJECTED;
        };
    }
}
