package com.penguinsecure.basis.strategy.basis.pricing;

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
import com.penguinsecure.basis.strategy.basis.model.AggressiveIocExecutionPolicyModel;
import com.penguinsecure.basis.strategy.basis.model.ConservativeHedgeRatioModel;
import com.penguinsecure.basis.strategy.basis.model.FundingSettlementCarryModel;
import com.penguinsecure.basis.strategy.basis.model.InversePayoffModel;
import com.penguinsecure.basis.strategy.basis.model.LinearPayoffModel;
import com.penguinsecure.basis.strategy.basis.model.ThresholdSignalModel;

final class StrategyTestFixtures {
    static final long DECISION_MONO = 1_000_100;
    static final long DECISION_EPOCH = 200;
    static final int RATE_SCALE = 8;

    private StrategyTestFixtures() {}

    static StrategyModelRegistry registry() {
        return new StrategyModelRegistry(8)
                .registerPayoff(new InversePayoffModel(1))
                .registerPayoff(new LinearPayoffModel(2))
                .registerPayoff(new LinearPayoffModel(3))
                .registerPayoff(new InversePayoffModel(4))
                .registerHedgeRatio(new ConservativeHedgeRatioModel(1))
                .registerCarryModel(new FundingSettlementCarryModel(1))
                .registerCarryModel(new FundingSettlementCarryModel(2))
                .registerSignalModel(new ThresholdSignalModel(1))
                .registerExecutionPolicy(new AggressiveIocExecutionPolicyModel(1))
                .freeze();
    }

    static BasisStrategyDefinition linearDefinition() {
        return definition(2, 2, 2, 1);
    }

    static BasisStrategyDefinition definition(
            final int strategyId,
            final int firstPayoffId,
            final int secondPayoffId,
            final int carryModelId) {
        return definition(
                strategyId,
                firstPayoffId,
                secondPayoffId,
                carryModelId,
                new StrategyRiskLimits(1_000, 1_000, 100, 10, 10_000_000, 10_000_000, 1));
    }

    static BasisStrategyDefinition definition(
            final int strategyId,
            final int firstPayoffId,
            final int secondPayoffId,
            final int carryModelId,
            final StrategyRiskLimits riskLimits) {
        return new BasisStrategyDefinition(
                strategyId,
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
                new StrategyModelIds(firstPayoffId, secondPayoffId, 1, carryModelId, 1, 1),
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
                riskLimits);
    }

    static InstrumentDefinition linearInstrument(final int venueId, final int instrumentId) {
        return instrument(venueId, instrumentId, ProductFamily.LINEAR_PERPETUAL, 0);
    }

    static InstrumentDefinition instrument(
            final int venueId,
            final int instrumentId,
            final ProductFamily family,
            final long expiry) {
        return new InstrumentDefinition(
                instrumentId,
                venueId,
                family,
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
                expiry,
                1);
    }

    static FixedDepthOrderBook book(
            final int venueId,
            final int instrumentId,
            final int feedProfileId,
            final long receive,
            final long bid,
            final long ask,
            final long quantity) {
        final FixedDepthOrderBook book =
                new FixedDepthOrderBook(
                        venueId,
                        instrumentId,
                        feedProfileId,
                        8,
                        1,
                        1,
                        10_000,
                        0,
                        BookSequenceMode.COMPLETE_IMAGE_MONOTONIC,
                        BookSequenceField.UPDATE_ID);
        book.apply(new Update(venueId, instrumentId, feedProfileId, receive, bid, ask, quantity));
        return book;
    }

    static EconomicInputs inputs(final long expiryEpochNanos) {
        return inputs(expiryEpochNanos, 1_000);
    }

    static EconomicInputs inputs(final long expiryEpochNanos, final long maximumRiskTableExposure) {
        final FeeSchedule firstFees =
                new FeeSchedule(50_000, 100_000, RATE_SCALE, metadata(1, 10, expiryEpochNanos));
        final FeeSchedule secondFees =
                new FeeSchedule(50_000, 100_000, RATE_SCALE, metadata(1, 11, expiryEpochNanos));
        final DirectionalRateSchedule firstFunding =
                new DirectionalRateSchedule(0, 0, RATE_SCALE, metadata(2, 12, expiryEpochNanos));
        final DirectionalRateSchedule secondFunding =
                new DirectionalRateSchedule(0, 0, RATE_SCALE, metadata(2, 13, expiryEpochNanos));
        final DirectionalRateSchedule settlement =
                new DirectionalRateSchedule(0, 0, RATE_SCALE, metadata(2, 14, expiryEpochNanos));
        final ConversionRate conversion =
                new ConversionRate(100_000_000, RATE_SCALE, metadata(3, 15, expiryEpochNanos));
        final EconomicRate conversionCost =
                new EconomicRate(0, RATE_SCALE, metadata(3, 16, expiryEpochNanos));
        final EconomicRate slippage =
                new EconomicRate(0, RATE_SCALE, metadata(5, 17, expiryEpochNanos));
        final EconomicRate reserve =
                new EconomicRate(0, RATE_SCALE, metadata(7, 18, expiryEpochNanos));
        final LiquidityRiskTable table =
                new LiquidityRiskTable(4, 6, RATE_SCALE, metadata(99, 19, expiryEpochNanos), 2)
                        .add(
                                BasisDirection.BUY_FIRST_SELL_SECOND,
                                1,
                                2,
                                maximumRiskTableExposure,
                                0,
                                10_000_000,
                                0)
                        .add(
                                BasisDirection.SELL_FIRST_BUY_SECOND,
                                1,
                                2,
                                maximumRiskTableExposure,
                                0,
                                10_000_000,
                                0)
                        .freeze();
        return new EconomicInputs(
                firstFees,
                secondFees,
                firstFunding,
                secondFunding,
                settlement,
                conversion,
                conversion,
                conversionCost,
                slippage,
                reserve,
                table);
    }

    static InputMetadata metadata(final int source, final long generation, final long expiry) {
        return new InputMetadata(source, generation, 100, 900_000, expiry, 500_000, 1, 900_000);
    }

    private static final class Update implements BookUpdateView {
        private final int venueId;
        private final int instrumentId;
        private final int feedProfileId;
        private final long receive;
        private final long bid;
        private final long ask;
        private final long quantity;

        private Update(
                final int venueId,
                final int instrumentId,
                final int feedProfileId,
                final long receive,
                final long bid,
                final long ask,
                final long quantity) {
            this.venueId = venueId;
            this.instrumentId = instrumentId;
            this.feedProfileId = feedProfileId;
            this.receive = receive;
            this.bid = bid;
            this.ask = ask;
            this.quantity = quantity;
        }

        public int venueId() {
            return venueId;
        }

        public int instrumentId() {
            return instrumentId;
        }

        public int feedProfileId() {
            return feedProfileId;
        }

        public BookUpdateType updateType() {
            return BookUpdateType.IMAGE;
        }

        public long sessionGeneration() {
            return 1;
        }

        public long receiveMonoNanos() {
            return receive;
        }

        public long venueTimestampMillis() {
            return receive;
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
            return quantity;
        }

        public int askCount() {
            return 1;
        }

        public long askPriceTicks(final int index) {
            return ask;
        }

        public long askQuantityLots(final int index) {
            return quantity;
        }
    }
}
