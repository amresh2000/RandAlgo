package com.penguinsecure.basis.benchmarks;

import com.penguinsecure.basis.core.book.BookSequenceField;
import com.penguinsecure.basis.core.book.BookSequenceMode;
import com.penguinsecure.basis.core.book.BookUpdateType;
import com.penguinsecure.basis.core.book.BookUpdateView;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.core.product.InstrumentLifecycle;
import com.penguinsecure.basis.core.product.ProductFamily;
import com.penguinsecure.basis.strategy.api.definition.BasisStrategyDefinition;
import com.penguinsecure.basis.strategy.api.definition.EconomicSourceIds;
import com.penguinsecure.basis.strategy.api.definition.StrategyLegDefinition;
import com.penguinsecure.basis.strategy.api.definition.StrategyLifecycle;
import com.penguinsecure.basis.strategy.api.definition.StrategyModelIds;
import com.penguinsecure.basis.strategy.api.definition.StrategyRiskLimits;
import com.penguinsecure.basis.strategy.api.model.StrategyModelRegistry;
import com.penguinsecure.basis.strategy.api.pricing.BasisDirection;
import com.penguinsecure.basis.strategy.api.pricing.ConversionRate;
import com.penguinsecure.basis.strategy.api.pricing.DirectionalRateSchedule;
import com.penguinsecure.basis.strategy.api.pricing.EconomicInputs;
import com.penguinsecure.basis.strategy.api.pricing.EconomicRate;
import com.penguinsecure.basis.strategy.api.pricing.FeeSchedule;
import com.penguinsecure.basis.strategy.api.pricing.InputMetadata;
import com.penguinsecure.basis.strategy.api.pricing.LiquidityRiskTable;
import com.penguinsecure.basis.strategy.api.pricing.MutableBasisOpportunity;
import com.penguinsecure.basis.strategy.basis.model.AggressiveIocExecutionPolicyModel;
import com.penguinsecure.basis.strategy.basis.model.ConservativeHedgeRatioModel;
import com.penguinsecure.basis.strategy.basis.model.FundingSettlementCarryModel;
import com.penguinsecure.basis.strategy.basis.model.LinearPayoffModel;
import com.penguinsecure.basis.strategy.basis.model.ThresholdSignalModel;
import com.penguinsecure.basis.strategy.basis.pricing.CrossVenueBasisStrategy;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Discovery benchmark for steady-state complete opportunity evaluation. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CrossVenueBasisStrategyBenchmark {
    @State(Scope.Thread)
    public static class PricingState {
        private CrossVenueBasisStrategy strategy;
        private FixedDepthOrderBook firstBook;
        private FixedDepthOrderBook secondBook;
        private EconomicInputs inputs;
        private MutableBasisOpportunity result;

        @Setup
        public void setup() {
            final InstrumentDefinition firstInstrument = instrument(1, 101);
            final InstrumentDefinition secondInstrument = instrument(2, 201);
            final StrategyModelRegistry registry =
                    new StrategyModelRegistry(4)
                            .registerPayoff(new LinearPayoffModel(2))
                            .registerHedgeRatio(new ConservativeHedgeRatioModel(1))
                            .registerCarryModel(new FundingSettlementCarryModel(1))
                            .registerSignalModel(new ThresholdSignalModel(1))
                            .registerExecutionPolicy(new AggressiveIocExecutionPolicyModel(1))
                            .freeze();
            strategy =
                    new CrossVenueBasisStrategy(
                            definition(), firstInstrument, secondInstrument, registry, 0, 2);
            firstBook = book(1, 101, 1, 1_000_000, 9_990, 10_000);
            secondBook = book(2, 201, 2, 1_000_010, 10_200, 10_210);
            inputs = inputs();
            result = new MutableBasisOpportunity();
        }
    }

    @Benchmark
    public long evaluateOpportunity(final PricingState state) {
        state.strategy.evaluate(
                BasisDirection.BUY_FIRST_SELL_SECOND,
                state.firstBook,
                state.secondBook,
                state.inputs,
                10,
                0,
                7,
                1_000_100,
                200,
                state.result);
        return state.result.netEdge();
    }

    private static BasisStrategyDefinition definition() {
        return new BasisStrategyDefinition(
                2,
                1,
                0,
                0,
                StrategyLifecycle.DRAFT,
                100,
                11,
                22,
                new StrategyLegDefinition(1, 101, 11, 1, 1_000),
                new StrategyLegDefinition(2, 201, 21, 2, 1_000),
                1,
                2,
                new StrategyModelIds(2, 2, 1, 1, 1, 1),
                new EconomicSourceIds(1, 2, 3, 4, 5, 6, 7),
                RoundingPolicy.TOWARD_ZERO,
                100,
                20,
                3_600_000_000_000L,
                1_000,
                100,
                50_000,
                100_000,
                50_000,
                100_000,
                new StrategyRiskLimits(1_000, 1_000, 100, 10, 10_000_000, 10_000_000, 1));
    }

    private static InstrumentDefinition instrument(final int venue, final int instrument) {
        return new InstrumentDefinition(
                instrument,
                venue,
                ProductFamily.LINEAR_PERPETUAL,
                InstrumentLifecycle.TRADING,
                1,
                2,
                2,
                2,
                2,
                0,
                0,
                1,
                1,
                1,
                10_000,
                1,
                0,
                1);
    }

    private static FixedDepthOrderBook book(
            final int venue,
            final int instrument,
            final int profile,
            final long receive,
            final long bid,
            final long ask) {
        final FixedDepthOrderBook book =
                new FixedDepthOrderBook(
                        venue,
                        instrument,
                        profile,
                        8,
                        1,
                        1,
                        10_000,
                        0,
                        BookSequenceMode.COMPLETE_IMAGE_MONOTONIC,
                        BookSequenceField.UPDATE_ID);
        book.apply(new Image(venue, instrument, profile, receive, bid, ask));
        return book;
    }

    private static EconomicInputs inputs() {
        final InputMetadata fee = metadata(1, 10);
        final InputMetadata funding = metadata(2, 11);
        final InputMetadata conversion = metadata(3, 12);
        final LiquidityRiskTable table =
                new LiquidityRiskTable(4, 6, 8, metadata(99, 16), 1)
                        .add(BasisDirection.BUY_FIRST_SELL_SECOND, 1, 2, 1_000, 0, 10_000_000, 0)
                        .freeze();
        return new EconomicInputs(
                new FeeSchedule(50_000, 100_000, 8, fee),
                new FeeSchedule(50_000, 100_000, 8, fee),
                new DirectionalRateSchedule(0, 0, 8, funding),
                new DirectionalRateSchedule(0, 0, 8, funding),
                new DirectionalRateSchedule(0, 0, 8, funding),
                new ConversionRate(100_000_000, 8, conversion),
                new ConversionRate(100_000_000, 8, conversion),
                new EconomicRate(0, 8, conversion),
                new EconomicRate(0, 8, metadata(5, 13)),
                new EconomicRate(0, 8, metadata(7, 14)),
                table);
    }

    private static InputMetadata metadata(final int source, final long generation) {
        return new InputMetadata(source, generation, 100, 900_000, 1_000, 500_000, 1, 900_000);
    }

    private record Image(
            int venueId,
            int instrumentId,
            int feedProfileId,
            long receiveMonoNanos,
            long bid,
            long ask)
            implements BookUpdateView {
        public BookUpdateType updateType() {
            return BookUpdateType.IMAGE;
        }

        public long sessionGeneration() {
            return 1;
        }

        public long venueTimestampMillis() {
            return receiveMonoNanos;
        }

        public long venueSequence() {
            return 1;
        }

        public long venueChangeId() {
            return 1;
        }

        public long venueUpdateId() {
            return 1;
        }

        public int validationFlags() {
            return 0;
        }

        public int bidCount() {
            return 1;
        }

        public long bidPriceTicks(final int index) {
            return bid;
        }

        public long bidQuantityLots(final int index) {
            return 1_000;
        }

        public int askCount() {
            return 1;
        }

        public long askPriceTicks(final int index) {
            return ask;
        }

        public long askQuantityLots(final int index) {
            return 1_000;
        }
    }
}
