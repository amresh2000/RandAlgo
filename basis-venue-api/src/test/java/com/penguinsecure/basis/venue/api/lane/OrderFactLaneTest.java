package com.penguinsecure.basis.venue.api.lane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.core.oems.fact.MutableOrderFact;
import com.penguinsecure.basis.core.oems.fact.OrderFactProvenance;
import com.penguinsecure.basis.core.oems.fact.OrderFactType;
import com.penguinsecure.basis.core.time.MonotonicClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class OrderFactLaneTest {
    @Test
    void roundTripsNormalizedFactAndReportsAge() {
        MonotonicClock clock = () -> 2_000;
        OrderFactLane lane = new OrderFactLane(2_048, new LaneHealthWord(), clock, 7);
        MutableOrderFact fact =
                new MutableOrderFact()
                        .set(
                                OrderFactType.FILL,
                                OrderFactProvenance.SIMULATED,
                                ((long) 1 << 32) | 3,
                                9,
                                1,
                                5,
                                3,
                                10_000,
                                1_900,
                                77,
                                4,
                                101,
                                0,
                                null,
                                0);
        assertTrue(lane.publish(fact));
        assertEquals(100, lane.oldestAgeNanos(2_000));

        long[] observed = new long[3];
        assertEquals(
                1,
                lane.drain(
                        decoded -> {
                            observed[0] = decoded.executionIdentityHash();
                            observed[1] = decoded.fillQuantity();
                            observed[2] = decoded.fillPriceTicks();
                        },
                        1));
        assertEquals(77, observed[0]);
        assertEquals(4, observed[1]);
        assertEquals(101, observed[2]);
        assertEquals(0, lane.oldestAgeNanos(2_000));
    }
}
