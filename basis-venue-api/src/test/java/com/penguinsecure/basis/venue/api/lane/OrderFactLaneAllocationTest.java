package com.penguinsecure.basis.venue.api.lane;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.penguinsecure.basis.core.oems.fact.MutableOrderFact;
import com.penguinsecure.basis.core.oems.fact.OrderFactHandler;
import com.penguinsecure.basis.core.oems.fact.OrderFactProvenance;
import com.penguinsecure.basis.core.oems.fact.OrderFactType;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
final class OrderFactLaneAllocationTest {
    private static final int WARMUP = 50_000;
    private static final int MEASURED = 100_000;

    @Test
    void publishAndDrainAllocateLessThanOneBytePerIteration() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        MonotonicClock clock = () -> 2_000;
        OrderFactLane lane = new OrderFactLane(2_048, new LaneHealthWord(), clock, 1);
        MutableOrderFact fact = new MutableOrderFact();
        OrderFactHandler handler = decoded -> consume(decoded.fillQuantity());
        exercise(lane, fact, handler, WARMUP);
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        exercise(lane, fact, handler, MEASURED);
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
        assertTrue(
                allocated < MEASURED,
                () -> "order fact lane allocated " + allocated + " measured bytes");
    }

    private static void exercise(
            final OrderFactLane lane,
            final MutableOrderFact fact,
            final OrderFactHandler handler,
            final int iterations) {
        for (int index = 0; index < iterations; index++) {
            fact.set(
                    OrderFactType.FILL,
                    OrderFactProvenance.ACTUAL,
                    ((long) 1 << 32) | 1,
                    index + 1L,
                    1,
                    5,
                    1,
                    10_000,
                    1_900,
                    index + 1L,
                    1,
                    100,
                    0,
                    null,
                    0);
            lane.publish(fact);
            lane.drain(handler, 1);
        }
    }

    private static void consume(final long value) {
        if (value == Long.MIN_VALUE) throw new AssertionError("unreachable");
    }
}
