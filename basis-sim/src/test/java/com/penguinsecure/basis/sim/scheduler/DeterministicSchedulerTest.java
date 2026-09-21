package com.penguinsecure.basis.sim.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penguinsecure.basis.sim.time.VirtualClock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeterministicSchedulerTest {
    @Test
    void ordersEqualTimesByPriorityProducerAndSequence() {
        VirtualClock clock = new VirtualClock(10_000, 100);
        DeterministicScheduler scheduler = new DeterministicScheduler(8, 4, clock);
        scheduler.schedule(200, 2, 1, 0, 1, 21, 0, 0, 0);
        scheduler.schedule(200, 1, 2, 0, 1, 12, 0, 0, 0);
        scheduler.schedule(200, 1, 1, 1, 1, 11, 0, 0, 0);
        scheduler.schedule(200, 1, 1, 2, 1, 111, 0, 0, 0);

        List<Long> order = new ArrayList<>();
        assertEquals(
                RunStatus.QUIESCENT,
                scheduler.runToQuiescence(
                        8,
                        300,
                        (time, priority, producer, sequence, kind, v0, v1, v2, v3) ->
                                order.add(v0)));

        assertEquals(List.of(11L, 111L, 12L, 21L), order);
        assertEquals(200, clock.nanoTime());
        assertEquals(10_100, clock.epochNanos());
    }

    @Test
    void rejectsProducerSequenceRegressionAndBackwardClock() {
        VirtualClock clock = new VirtualClock(1_000, 10);
        DeterministicScheduler scheduler = new DeterministicScheduler(2, 1, clock);
        assertEquals(ScheduleStatus.OK, scheduler.schedule(20, 1, 0, 1, 1, 0, 0, 0, 0));
        assertEquals(
                ScheduleStatus.NON_MONOTONIC_PRODUCER_SEQUENCE,
                scheduler.schedule(20, 1, 0, 1, 1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> clock.advanceTo(9));
    }

    @Test
    void modelsAsymmetricFeedDelayAndArrivalSkewWithoutWallTime() {
        VirtualClock clock = new VirtualClock(1_000_000, 100);
        DeterministicScheduler scheduler = new DeterministicScheduler(4, 2, clock);
        scheduler.schedule(400, 10, 0, 0, 7, 1_000, 0, 0, 0);
        scheduler.schedule(250, 10, 1, 0, 7, 1_005, 0, 0, 0);
        List<Long> arrivals = new ArrayList<>();

        scheduler.runToQuiescence(
                4,
                500,
                (time, priority, producer, sequence, kind, venueTime, v1, v2, v3) -> {
                    arrivals.add(time);
                    arrivals.add(venueTime);
                });

        assertEquals(List.of(250L, 1_005L, 400L, 1_000L), arrivals);
        assertEquals(400, clock.nanoTime());
    }
}
