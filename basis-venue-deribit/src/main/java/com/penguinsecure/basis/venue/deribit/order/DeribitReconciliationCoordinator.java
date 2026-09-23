package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.order.MutableVenueOrderFact;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactSink;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactType;
import com.penguinsecure.basis.venue.api.order.VenueOrderState;

/** Complete-only merge of bounded Deribit open-order and history evidence. */
public final class DeribitReconciliationCoordinator {
    private final DeribitOrderProfile profile;
    private final DeribitReconciliationTransport transport;
    private final DeribitReconciliationPage page;
    private final VenueOrderFactSink facts;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final int maximumPages;
    private final long[] high, low, filled;
    private final VenueOrderState[] states;
    private final MutableVenueOrderFact fact = new MutableVenueOrderFact();
    private int size;

    @SuppressWarnings("ParameterNumber")
    public DeribitReconciliationCoordinator(
            final DeribitOrderProfile profile,
            final DeribitReconciliationTransport transport,
            final DeribitReconciliationPage page,
            final VenueOrderFactSink facts,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock,
            final int maximumPages) {
        if (profile == null
                || transport == null
                || page == null
                || facts == null
                || epochClock == null
                || monotonicClock == null)
            throw new NullPointerException("dependencies are required");
        if (maximumPages <= 0) throw new IllegalArgumentException("maximumPages must be positive");
        this.profile = profile;
        this.transport = transport;
        this.page = page;
        this.facts = facts;
        this.epochClock = epochClock;
        this.monotonicClock = monotonicClock;
        this.maximumPages = maximumPages;
        high = new long[Math.multiplyExact(maximumPages, page.capacity() * 2)];
        low = new long[high.length];
        filled = new long[high.length];
        states = new VenueOrderState[high.length];
    }

    public DeribitReconciliationStatus reconcileOrders(final long oldestUnresolvedEpochMillis) {
        if (oldestUnresolvedEpochMillis < 0) return DeribitReconciliationStatus.INVALID_RESULT;
        size = 0;
        DeribitReconciliationStatus status = collect(DeribitReconciliationEndpoint.OPEN_ORDERS, 0);
        if (status != DeribitReconciliationStatus.COMPLETE) return status;
        status = collect(DeribitReconciliationEndpoint.ORDER_HISTORY, oldestUnresolvedEpochMillis);
        if (status != DeribitReconciliationStatus.COMPLETE) return status;
        for (int index = 0; index < size; index++) {
            fact.set(
                    VenueOrderFactType.RECONCILED,
                    high[index],
                    low[index],
                    profile.venueId(),
                    profile.instrumentId(),
                    high[index] & 0xffff_ffffL,
                    epochClock.epochNanos(),
                    monotonicClock.nanoTime(),
                    0,
                    0,
                    0,
                    filled[index],
                    states[index],
                    0);
            if (!facts.publish(fact)) return DeribitReconciliationStatus.FACT_LANE_FULL;
        }
        return DeribitReconciliationStatus.COMPLETE;
    }

    private DeribitReconciliationStatus collect(
            final DeribitReconciliationEndpoint endpoint, final long start) {
        int offset = 0;
        for (int pageIndex = 0; pageIndex < maximumPages; pageIndex++) {
            page.reset();
            if (!transport.fetch(endpoint, offset, start, page))
                return DeribitReconciliationStatus.TRANSPORT_FAILED;
            for (int index = 0; index < page.size(); index++)
                if (!merge(index)) return DeribitReconciliationStatus.INVALID_RESULT;
            if (!page.hasMore()) return DeribitReconciliationStatus.COMPLETE;
            if (page.nextOffset() <= offset) return DeribitReconciliationStatus.INVALID_RESULT;
            offset = page.nextOffset();
        }
        return DeribitReconciliationStatus.PAGE_LIMIT_EXCEEDED;
    }

    private boolean merge(final int source) {
        for (int index = 0; index < size; index++) {
            if (high[index] != page.idHigh(source) || low[index] != page.idLow(source)) continue;
            if (filled[index] == page.filled(source) && states[index] == page.state(source))
                return true;
            if (terminal(states[index]) && !terminal(page.state(source))) return true;
            if (terminal(page.state(source)) && page.filled(source) >= filled[index]) {
                filled[index] = page.filled(source);
                states[index] = page.state(source);
                return true;
            }
            return false;
        }
        if (size == high.length) return false;
        high[size] = page.idHigh(source);
        low[size] = page.idLow(source);
        filled[size] = page.filled(source);
        states[size] = page.state(source);
        size++;
        return true;
    }

    private static boolean terminal(final VenueOrderState state) {
        return state == VenueOrderState.FILLED
                || state == VenueOrderState.CANCELLED
                || state == VenueOrderState.REJECTED;
    }
}
