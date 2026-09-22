package com.penguinsecure.basis.strategy.basis.pricing;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.strategy.api.pricing.BasisDirection;
import com.penguinsecure.basis.strategy.api.pricing.EconomicInputs;
import com.penguinsecure.basis.strategy.api.pricing.MutableBasisOpportunity;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
final class CrossVenueBasisStrategyAllocationTest {
    private static final int WARMUP = 50_000;
    private static final int ITERATIONS = 100_000;

    @Test
    void steadyStateEvaluationAllocatesLessThanOneBytePerIteration() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        InstrumentDefinition firstInstrument = StrategyTestFixtures.linearInstrument(1, 101);
        InstrumentDefinition secondInstrument = StrategyTestFixtures.linearInstrument(2, 201);
        FixedDepthOrderBook firstBook =
                StrategyTestFixtures.book(1, 101, 1, 1_000_000, 9_990, 10_000, 1_000);
        FixedDepthOrderBook secondBook =
                StrategyTestFixtures.book(2, 201, 2, 1_000_010, 10_200, 10_210, 1_000);
        CrossVenueBasisStrategy strategy =
                new CrossVenueBasisStrategy(
                        StrategyTestFixtures.linearDefinition(),
                        firstInstrument,
                        secondInstrument,
                        StrategyTestFixtures.registry(),
                        0,
                        2);
        EconomicInputs inputs = StrategyTestFixtures.inputs(1_000);
        MutableBasisOpportunity result = new MutableBasisOpportunity();

        exercise(strategy, firstBook, secondBook, inputs, result, WARMUP);
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        exercise(strategy, firstBook, secondBook, inputs, result, ITERATIONS);
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;

        assertTrue(allocated < ITERATIONS, () -> "pricing allocated " + allocated + " bytes");
    }

    private static void exercise(
            final CrossVenueBasisStrategy strategy,
            final FixedDepthOrderBook firstBook,
            final FixedDepthOrderBook secondBook,
            final EconomicInputs inputs,
            final MutableBasisOpportunity result,
            final int iterations) {
        for (int index = 0; index < iterations; index++) {
            strategy.evaluate(
                    BasisDirection.BUY_FIRST_SELL_SECOND,
                    firstBook,
                    secondBook,
                    inputs,
                    10,
                    0,
                    7,
                    StrategyTestFixtures.DECISION_MONO,
                    StrategyTestFixtures.DECISION_EPOCH,
                    result);
        }
    }
}
