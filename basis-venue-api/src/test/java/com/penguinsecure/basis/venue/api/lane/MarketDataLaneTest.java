package com.penguinsecure.basis.venue.api.lane;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class MarketDataLaneTest {
    @Test
    void roundTripsFixedLayoutAndCommitTimestamp() {
        LaneHealthWord health = new LaneHealthWord();
        MarketDataLane lane = new MarketDataLane(1 << 15, 20, health, () -> 999, 7);
        MutableMarketDataEvent event = event(3);
        assertTrue(lane.publish(event));
        AtomicInteger calls = new AtomicInteger();
        assertEquals(
                1,
                lane.drain(
                        decoded -> {
                            calls.incrementAndGet();
                            assertEquals(101, decoded.instrumentId());
                            assertEquals(MarketDataEventKind.SNAPSHOT, decoded.kind());
                            assertEquals(2, decoded.bidCount());
                            assertEquals(3, decoded.askCount());
                            assertEquals(100_000, decoded.bidPriceTicks(0));
                            assertEquals(999, decoded.ringCommitMonoNanos());
                            assertEquals(88, decoded.venueUpdateId());
                        },
                        1));
        assertEquals(1, calls.get());
    }

    @Test
    void fullRingPublishesIndependentOverflowState() {
        LaneHealthWord health = new LaneHealthWord();
        MarketDataLane lane = new MarketDataLane(1 << 14, 20, health, () -> 999, 7);
        MutableMarketDataEvent event = event(20);
        while (lane.publish(event)) {
            // Deliberately stall the consumer.
        }
        LaneHealthSnapshot snapshot = new LaneHealthSnapshot();
        health.read(snapshot);
        assertEquals(LaneHealthState.OVERFLOW, snapshot.state());
        assertEquals(VenueFailureReason.RING_OVERFLOW, snapshot.reason());
        assertEquals(7, snapshot.producerEpoch());
        assertEquals(101, snapshot.affectedScope());
    }

    @Test
    void rejectsIncompleteEventWithoutPublishing() {
        MarketDataLane lane = new MarketDataLane(1 << 14, 20, new LaneHealthWord(), () -> 1, 1);
        assertFalse(lane.publish(new MutableMarketDataEvent(20)));
        assertEquals(0, lane.sizeBytes());
    }

    private static MutableMarketDataEvent event(int levels) {
        MutableMarketDataEvent event = new MutableMarketDataEvent(20);
        event.venueId(1);
        event.instrumentId(101);
        event.feedProfileId(9);
        event.kind(MarketDataEventKind.SNAPSHOT);
        event.sessionGeneration(4);
        event.receiveEpochNanos(11);
        event.receiveMonoNanos(12);
        event.decodeCompleteMonoNanos(13);
        event.venueTimestampMillis(14);
        event.matchingEngineTimestampMillis(15);
        event.venueSequence(77);
        event.venueUpdateId(88);
        for (int i = 0; i < levels; i++) {
            event.addAsk(101_000 + i, 10 + i);
            if (i < 2 || levels == 20) event.addBid(100_000 - i, 20 + i);
        }
        return event;
    }
}
