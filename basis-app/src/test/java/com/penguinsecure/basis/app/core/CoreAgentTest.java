package com.penguinsecure.basis.app.core;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.lane.MarketDataLane;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class CoreAgentTest {
    @Test
    void samplesHealthBeforeFairQuotaBoundedDrain() {
        LaneHealthWord firstHealth = new LaneHealthWord();
        LaneHealthWord secondHealth = new LaneHealthWord();
        MarketDataLane first = lane(firstHealth);
        MarketDataLane second = lane(secondHealth);
        firstHealth.publish(LaneHealthState.HEALTHY, VenueFailureReason.NONE, 1, 1, 0);
        secondHealth.publish(LaneHealthState.OVERFLOW, VenueFailureReason.RING_OVERFLOW, 2, 1, 202);
        for (int i = 0; i < 5; i++) first.publish(event(101));
        second.publish(event(202));
        List<String> order = new ArrayList<>();
        CoreAgent agent =
                new CoreAgent(
                        new MarketDataLane[] {first, second},
                        1,
                        event -> order.add("event:" + event.instrumentId()),
                        (index, health) -> order.add("fault:" + index));

        assertEquals(3, agent.doWork());
        assertEquals(List.of("fault:1", "event:101", "event:202"), order);
        order.clear();
        agent.doWork();
        assertEquals(List.of("event:101"), order);
    }

    private static MarketDataLane lane(LaneHealthWord health) {
        return new MarketDataLane(1 << 14, 2, health, () -> 20, 1);
    }

    private static MutableMarketDataEvent event(int instrumentId) {
        MutableMarketDataEvent event = new MutableMarketDataEvent(2);
        event.venueId(1);
        event.instrumentId(instrumentId);
        event.feedProfileId(1);
        event.kind(MarketDataEventKind.SNAPSHOT);
        event.sessionGeneration(1);
        event.receiveMonoNanos(1);
        event.decodeCompleteMonoNanos(2);
        return event;
    }
}
