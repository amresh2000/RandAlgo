package com.penguinsecure.basis.app.marketdata;

import com.penguinsecure.basis.core.book.BookMutationStatus;
import com.penguinsecure.basis.core.book.BookRejectionReason;
import com.penguinsecure.basis.core.book.BookSide;
import com.penguinsecure.basis.core.book.BookTrustState;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.lane.MarketDataEventHandler;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import java.util.Locale;

/** Core-side latency probe which also proves normalized events can mutate a real book. */
final class VenueLatencyProbe implements MarketDataEventHandler {
    private static final int WINDOW_CAPACITY = 65_536;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private final String venue;
    private final FixedDepthOrderBook book;
    private final MonotonicClock monotonicClock;
    private final LatencyWindow venueToReceive = new LatencyWindow(WINDOW_CAPACITY);
    private final LatencyWindow receiveToDecode = new LatencyWindow(WINDOW_CAPACITY);
    private final LatencyWindow publishToDequeue = new LatencyWindow(WINDOW_CAPACITY);
    private final LatencyWindow receiveToBook = new LatencyWindow(WINDOW_CAPACITY);
    private final LatencyWindow bookApply = new LatencyWindow(WINDOW_CAPACITY);
    private long events;
    private long applied;
    private long rejected;
    private long invalidClockSamples;
    private BookRejectionReason firstRejection = BookRejectionReason.NONE;

    VenueLatencyProbe(
            final String venue,
            final FixedDepthOrderBook book,
            final MonotonicClock monotonicClock) {
        if (venue == null || book == null || monotonicClock == null) {
            throw new NullPointerException("probe dependencies are required");
        }
        this.venue = venue;
        this.book = book;
        this.monotonicClock = monotonicClock;
    }

    @Override
    public void onMarketData(final MutableMarketDataEvent event) {
        final long dequeueNanos = monotonicClock.nanoTime();
        events++;
        recordIfOrdered(receiveToDecode, event.decodeCompleteMonoNanos(), event.receiveMonoNanos());
        recordIfOrdered(publishToDequeue, dequeueNanos, event.ringCommitMonoNanos());

        final long venueMillis =
                event.matchingEngineTimestampMillis() > 0
                        ? event.matchingEngineTimestampMillis()
                        : event.venueTimestampMillis();
        final long venueNanos;
        try {
            venueNanos = Math.multiplyExact(venueMillis, NANOS_PER_MILLISECOND);
        } catch (ArithmeticException exception) {
            invalidClockSamples++;
            apply(event, dequeueNanos);
            return;
        }
        final long wireNanos = event.receiveEpochNanos() - venueNanos;
        if (venueMillis > 0 && wireNanos >= 0) {
            venueToReceive.record(wireNanos);
        } else {
            invalidClockSamples++;
        }
        apply(event, dequeueNanos);
    }

    String report() {
        return String.format(
                Locale.ROOT,
                "%s events=%d applied=%d rejected=%d firstReject=%s clockInvalid=%d book=%s"
                        + " reason=%s bid=%d ask=%d%n  venue->receive %s%n  receive->decode %s%n "
                        + " publish->dequeue %s%n  receive->book %s%n  book-apply %s",
                venue,
                events,
                applied,
                rejected,
                firstRejection,
                invalidClockSamples,
                book.trustState(),
                book.rejectionReason(),
                best(BookSide.BID),
                best(BookSide.ASK),
                format(venueToReceive.snapshot()),
                format(receiveToDecode.snapshot()),
                format(publishToDequeue.snapshot()),
                format(receiveToBook.snapshot()),
                format(bookApply.snapshot()));
    }

    long events() {
        return events;
    }

    long applied() {
        return applied;
    }

    boolean isTrustedWithoutRejections() {
        return book.trustState() == BookTrustState.TRUSTED && rejected == 0;
    }

    private void apply(final MutableMarketDataEvent event, final long dequeueNanos) {
        final long applyStart = monotonicClock.nanoTime();
        if (book.apply(event) == BookMutationStatus.APPLIED) {
            applied++;
        } else {
            rejected++;
            if (firstRejection == BookRejectionReason.NONE) {
                firstRejection = book.rejectionReason();
            }
        }
        final long applyEnd = monotonicClock.nanoTime();
        recordIfOrdered(bookApply, applyEnd, applyStart);
        recordIfOrdered(receiveToBook, applyEnd, event.receiveMonoNanos());
    }

    private long best(final BookSide side) {
        return book.depth(side) == 0 ? 0 : book.bestPriceTicks(side);
    }

    private static void recordIfOrdered(
            final LatencyWindow histogram, final long later, final long earlier) {
        if (earlier > 0 && later >= earlier) histogram.record(later - earlier);
    }

    private static String format(final LatencyWindow.Snapshot snapshot) {
        return String.format(
                Locale.ROOT,
                "samples=%d retained=%d p50=%.3fus p90=%.3fus p99=%.3fus p99.9=%.3fus max=%.3fus",
                snapshot.totalSamples(),
                snapshot.retainedSamples(),
                micros(snapshot.p50Nanos()),
                micros(snapshot.p90Nanos()),
                micros(snapshot.p99Nanos()),
                micros(snapshot.p999Nanos()),
                micros(snapshot.maximumNanos()));
    }

    private static double micros(final long nanos) {
        return nanos / 1_000.0;
    }
}
