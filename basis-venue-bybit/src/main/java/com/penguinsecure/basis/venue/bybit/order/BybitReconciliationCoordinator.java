package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.order.MutableVenueOrderFact;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactSink;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactType;
import com.penguinsecure.basis.venue.api.order.VenueOrderState;

/** Bounded cursor-paged reconciliation publisher. Completion means every page was consumed. */
public final class BybitReconciliationCoordinator {
    private final BybitOrderProfile profile;
    private final BybitReconciliationTransport transport;
    private final BybitReconciliationPage page;
    private final VenueOrderFactSink facts;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final int maximumPages;
    private final long[] resultHigh;
    private final long[] resultLow;
    private final long[] resultFilled;
    private final VenueOrderState[] resultState;
    private final MutableVenueOrderFact fact = new MutableVenueOrderFact();
    private int resultSize;

    @SuppressWarnings("ParameterNumber")
    public BybitReconciliationCoordinator(
            final BybitOrderProfile profile,
            final BybitReconciliationTransport transport,
            final BybitReconciliationPage page,
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
            throw new NullPointerException("reconciliation dependencies are required");
        if (maximumPages <= 0) throw new IllegalArgumentException("maximumPages must be positive");
        this.profile = profile;
        this.transport = transport;
        this.page = page;
        this.facts = facts;
        this.epochClock = epochClock;
        this.monotonicClock = monotonicClock;
        this.maximumPages = maximumPages;
        resultHigh = new long[Math.multiplyExact(maximumPages, page.capacity())];
        resultLow = new long[resultHigh.length];
        resultFilled = new long[resultHigh.length];
        resultState = new VenueOrderState[resultHigh.length];
    }

    public BybitReconciliationStatus reconcileOrders(
            final long sessionGeneration, final long oldestUnresolvedEpochMillis) {
        if (sessionGeneration <= 0 || oldestUnresolvedEpochMillis < 0) {
            return BybitReconciliationStatus.INVALID_RESULT;
        }
        resultSize = 0;
        BybitReconciliationStatus status = collect(BybitReconciliationEndpoint.OPEN_ORDERS, 0);
        if (status != BybitReconciliationStatus.COMPLETE) return status;
        status = collect(BybitReconciliationEndpoint.ORDER_HISTORY, oldestUnresolvedEpochMillis);
        if (status != BybitReconciliationStatus.COMPLETE) return status;
        for (int index = 0; index < resultSize; index++) {
            fact.set(
                    VenueOrderFactType.RECONCILED,
                    resultHigh[index],
                    resultLow[index],
                    profile.venueId(),
                    profile.instrumentId(),
                    resultHigh[index] & 0xffff_ffffL,
                    epochClock.epochNanos(),
                    monotonicClock.nanoTime(),
                    0,
                    0,
                    0,
                    resultFilled[index],
                    resultState[index],
                    0);
            if (!facts.publish(fact)) return BybitReconciliationStatus.FACT_LANE_FULL;
        }
        return BybitReconciliationStatus.COMPLETE;
    }

    private BybitReconciliationStatus collect(
            final BybitReconciliationEndpoint endpoint, final long startTimeMillis) {
        final byte[] cursor = page.nextCursorBytes();
        int cursorLength = 0;
        for (int pageIndex = 0; pageIndex < maximumPages; pageIndex++) {
            page.reset();
            if (!transport.fetch(endpoint, cursor, cursorLength, startTimeMillis, page))
                return BybitReconciliationStatus.TRANSPORT_FAILED;
            for (int index = 0; index < page.size(); index++) {
                if (!merge(page, index)) return BybitReconciliationStatus.INVALID_RESULT;
            }
            cursorLength = page.nextCursorLength();
            if (cursorLength == 0) return BybitReconciliationStatus.COMPLETE;
        }
        return BybitReconciliationStatus.PAGE_LIMIT_EXCEEDED;
    }

    private boolean merge(final BybitReconciliationPage source, final int sourceIndex) {
        for (int index = 0; index < resultSize; index++) {
            if (resultHigh[index] != source.idHigh(sourceIndex)
                    || resultLow[index] != source.idLow(sourceIndex)) continue;
            if (resultFilled[index] == source.filled(sourceIndex)
                    && resultState[index] == source.state(sourceIndex)) return true;
            if (terminal(resultState[index]) && !terminal(source.state(sourceIndex))) return true;
            if (terminal(source.state(sourceIndex))
                    && source.filled(sourceIndex) >= resultFilled[index]) {
                resultFilled[index] = source.filled(sourceIndex);
                resultState[index] = source.state(sourceIndex);
                return true;
            }
            return false;
        }
        if (resultSize == resultHigh.length) return false;
        resultHigh[resultSize] = source.idHigh(sourceIndex);
        resultLow[resultSize] = source.idLow(sourceIndex);
        resultFilled[resultSize] = source.filled(sourceIndex);
        resultState[resultSize] = source.state(sourceIndex);
        resultSize++;
        return true;
    }

    private static boolean terminal(final VenueOrderState state) {
        return state == VenueOrderState.FILLED
                || state == VenueOrderState.CANCELLED
                || state == VenueOrderState.REJECTED;
    }
}
