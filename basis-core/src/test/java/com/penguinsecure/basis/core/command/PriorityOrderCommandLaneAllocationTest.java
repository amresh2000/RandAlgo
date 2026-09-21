package com.penguinsecure.basis.core.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
final class PriorityOrderCommandLaneAllocationTest {
    private static final int WARMUP = 50_000;
    private static final int MEASURED = 100_000;

    @Test
    void publishAndDrainAllocateLessThanOneBytePerIteration() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        PriorityOrderCommandLane lane = new PriorityOrderCommandLane(8, 8);
        OrderCommandHandler handler = command -> consume(command.quantity());
        exercise(lane, handler, WARMUP);
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        exercise(lane, handler, MEASURED);
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
        assertTrue(
                allocated < MEASURED,
                () -> "command lane allocated " + allocated + " bytes in measured iterations");
    }

    private static void exercise(
            final PriorityOrderCommandLane lane,
            final OrderCommandHandler handler,
            final int iterations) {
        for (int index = 0; index < iterations; index++) {
            lane.tryPublish(
                    OrderCommandType.SUBMIT,
                    OrderUrgency.URGENT,
                    1,
                    index,
                    1,
                    2,
                    OrderSide.SELL,
                    10,
                    100,
                    index + 1L);
            lane.drainPrioritized(handler, 1);
        }
    }

    private static void consume(final long value) {
        if (value == Long.MIN_VALUE) throw new AssertionError("unreachable");
    }
}
