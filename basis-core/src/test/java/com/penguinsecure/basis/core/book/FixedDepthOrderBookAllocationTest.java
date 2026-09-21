package com.penguinsecure.basis.core.book;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
final class FixedDepthOrderBookAllocationTest {
    private static final int WARMUP_ITERATIONS = 50_000;
    private static final int MEASURED_ITERATIONS = 100_000;

    @Test
    void deltaAndExecutableQueryAllocateLessThanOneBytePerIteration() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        long threadId = Thread.currentThread().threadId();
        FixedDepthOrderBook book =
                new FixedDepthOrderBook(
                        1,
                        101,
                        11,
                        8,
                        1,
                        1,
                        Long.MAX_VALUE,
                        0,
                        BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC,
                        BookSequenceField.UPDATE_ID);
        BookTestUpdate update =
                new BookTestUpdate().reset(BookUpdateType.SNAPSHOT, 1).bid(100, 100).ask(101, 100);
        book.apply(update);
        MutableExecutablePrice result = new MutableExecutablePrice();

        exercise(book, update, result, WARMUP_ITERATIONS, 2);
        long before = bean.getThreadAllocatedBytes(threadId);
        exercise(book, update, result, MEASURED_ITERATIONS, WARMUP_ITERATIONS + 2L);
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertTrue(
                allocated < MEASURED_ITERATIONS,
                () -> "book allocated " + allocated + " bytes in measured iterations");
    }

    private static void exercise(
            final FixedDepthOrderBook book,
            final BookTestUpdate update,
            final MutableExecutablePrice result,
            final int iterations,
            final long firstSequence) {
        for (int index = 0; index < iterations; index++) {
            final long sequence = firstSequence + index;
            book.apply(
                    update.reset(BookUpdateType.DELTA, sequence)
                            .bid(100, (index & 1) == 0 ? 100 : 101));
            book.executablePrice(BookSide.ASK, 10, 101, sequence, result);
        }
    }
}
