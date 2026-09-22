package com.penguinsecure.basis.strategy.basis.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.core.book.BookTrustState;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.core.product.ProductFamily;
import com.penguinsecure.basis.strategy.api.definition.StrategyRiskLimits;
import com.penguinsecure.basis.strategy.api.pricing.BasisDirection;
import com.penguinsecure.basis.strategy.api.pricing.MutableBasisOpportunity;
import com.penguinsecure.basis.strategy.api.pricing.PricingStatus;
import org.junit.jupiter.api.Test;

final class CrossVenueBasisStrategyTest {
    @Test
    void emitsCompleteExecutableOpportunityAndCostDecomposition() {
        Fixture fixture = new Fixture(1_000_000, 1_000_010, 9_990, 10_000, 10_200, 10_210);

        PricingStatus status = fixture.evaluate(BasisDirection.BUY_FIRST_SELL_SECOND, 10);

        assertEquals(PricingStatus.OPPORTUNITY, status);
        assertEquals(10, fixture.result.canonicalExposure());
        assertEquals(1_798, fixture.result.netEdge());
        assertEquals(100, fixture.result.firstFee());
        assertEquals(102, fixture.result.secondFee());
        assertEquals(90, fixture.result.maximumCanonicalExposure());
        assertEquals(10, fixture.result.receiveSkewNanos());
        assertEquals(1, fixture.result.firstBookSequence());
        assertEquals(2, fixture.result.secondFeedProfileId());
        assertEquals(1_001_100, fixture.result.expiryMonoNanos());
        assertEquals(10, fixture.result.firstFeeGeneration());
        assertEquals(11, fixture.result.secondFeeGeneration());
        assertEquals(12, fixture.result.firstFundingGeneration());
        assertEquals(13, fixture.result.secondFundingGeneration());
        assertEquals(14, fixture.result.settlementGeneration());
        assertEquals(15, fixture.result.firstConversionGeneration());
        assertEquals(15, fixture.result.secondConversionGeneration());
        assertEquals(16, fixture.result.conversionCostGeneration());
        assertEquals(17, fixture.result.slippageGeneration());
        assertEquals(18, fixture.result.safetyReserveGeneration());
        assertEquals(19, fixture.result.liquidityRiskGeneration());
        assertTrue(fixture.result.netEdgeRate() > 0);
    }

    @Test
    void evaluatesTheReverseDirectionWithTheSameReusableModel() {
        Fixture fixture = new Fixture(1_000_000, 1_000_010, 10_200, 10_210, 9_990, 10_000);

        assertEquals(
                PricingStatus.OPPORTUNITY,
                fixture.evaluate(BasisDirection.SELL_FIRST_BUY_SECOND, 10));
        assertEquals(BasisDirection.SELL_FIRST_BUY_SECOND, fixture.result.direction());
    }

    @Test
    void evaluatesTheReferenceInversePerpetualWithoutEngineChanges() {
        InstrumentDefinition firstInstrument =
                StrategyTestFixtures.instrument(1, 101, ProductFamily.INVERSE_PERPETUAL, 0);
        InstrumentDefinition secondInstrument =
                StrategyTestFixtures.instrument(2, 201, ProductFamily.INVERSE_PERPETUAL, 0);
        FixedDepthOrderBook firstBook =
                StrategyTestFixtures.book(1, 101, 1, 1_000_000, 4_990_000, 5_000_000, 10_000);
        FixedDepthOrderBook secondBook =
                StrategyTestFixtures.book(2, 201, 2, 1_000_010, 5_100_000, 5_110_000, 10_000);
        CrossVenueBasisStrategy strategy =
                new CrossVenueBasisStrategy(
                        StrategyTestFixtures.definition(
                                1,
                                1,
                                1,
                                1,
                                new StrategyRiskLimits(
                                        100_000_000,
                                        100_000_000,
                                        10_000_000,
                                        10,
                                        100_000_000,
                                        100_000_000,
                                        1)),
                        firstInstrument,
                        secondInstrument,
                        StrategyTestFixtures.registry(),
                        8,
                        2);
        MutableBasisOpportunity result = new MutableBasisOpportunity();

        assertEquals(
                PricingStatus.OPPORTUNITY,
                strategy.evaluate(
                        BasisDirection.BUY_FIRST_SELL_SECOND,
                        firstBook,
                        secondBook,
                        StrategyTestFixtures.inputs(1_000, 100_000_000),
                        10_000_000,
                        0,
                        7,
                        StrategyTestFixtures.DECISION_MONO,
                        StrategyTestFixtures.DECISION_EPOCH,
                        result));
        assertEquals(5_000, result.firstNativeQuantity());
        assertEquals(5_100, result.secondNativeQuantity());
        assertEquals(10_000_000, result.canonicalExposure());
        assertEquals(8_990, result.netEdge());
    }

    @Test
    void rejectsTemporalSkewWithoutCorruptingEitherBook() {
        Fixture fixture = new Fixture(999_200, 1_000_000, 9_990, 10_000, 10_200, 10_210);

        assertEquals(
                PricingStatus.PRICE_NOT_TEMPORALLY_COHERENT,
                fixture.evaluate(BasisDirection.BUY_FIRST_SELL_SECOND, 10));
        assertEquals(BookTrustState.TRUSTED, fixture.firstBook.trustState());
        assertEquals(BookTrustState.TRUSTED, fixture.secondBook.trustState());
    }

    @Test
    void rejectsExpiredEconomicInputAndInsufficientDepth() {
        Fixture fixture = new Fixture(1_000_000, 1_000_010, 9_990, 10_000, 10_200, 10_210);
        assertEquals(
                PricingStatus.OPPORTUNITY,
                fixture.evaluate(BasisDirection.BUY_FIRST_SELL_SECOND, 10));
        assertEquals(
                PricingStatus.ECONOMIC_INPUT_INVALID,
                fixture.strategy.evaluate(
                        BasisDirection.BUY_FIRST_SELL_SECOND,
                        fixture.firstBook,
                        fixture.secondBook,
                        StrategyTestFixtures.inputs(200),
                        10,
                        0,
                        7,
                        StrategyTestFixtures.DECISION_MONO,
                        StrategyTestFixtures.DECISION_EPOCH,
                        fixture.result));
        assertEquals(0, fixture.result.canonicalExposure());
        assertEquals(0, fixture.result.configurationGeneration());

        Fixture shallow = new Fixture(1_000_000, 1_000_010, 9_990, 10_000, 10_200, 10_210, 1);
        assertEquals(
                PricingStatus.NO_EXECUTABLE_DEPTH,
                shallow.evaluate(BasisDirection.BUY_FIRST_SELL_SECOND, 10));
    }

    @Test
    void acceptsExactAgeAndSkewBoundaries() {
        Fixture fixture = new Fixture(999_100, 999_200, 9_990, 10_000, 10_200, 10_210);

        assertEquals(
                PricingStatus.OPPORTUNITY,
                fixture.evaluate(BasisDirection.BUY_FIRST_SELL_SECOND, 10));
        assertEquals(1_000, fixture.result.firstAgeNanos());
        assertEquals(100, fixture.result.receiveSkewNanos());
    }

    private static final class Fixture {
        private final FixedDepthOrderBook firstBook;
        private final FixedDepthOrderBook secondBook;
        private final CrossVenueBasisStrategy strategy;
        private final MutableBasisOpportunity result = new MutableBasisOpportunity();

        private Fixture(
                final long firstReceive,
                final long secondReceive,
                final long firstBid,
                final long firstAsk,
                final long secondBid,
                final long secondAsk) {
            this(firstReceive, secondReceive, firstBid, firstAsk, secondBid, secondAsk, 100);
        }

        private Fixture(
                final long firstReceive,
                final long secondReceive,
                final long firstBid,
                final long firstAsk,
                final long secondBid,
                final long secondAsk,
                final long quantity) {
            InstrumentDefinition firstInstrument = StrategyTestFixtures.linearInstrument(1, 101);
            InstrumentDefinition secondInstrument = StrategyTestFixtures.linearInstrument(2, 201);
            firstBook =
                    StrategyTestFixtures.book(
                            1, 101, 1, firstReceive, firstBid, firstAsk, quantity);
            secondBook =
                    StrategyTestFixtures.book(
                            2, 201, 2, secondReceive, secondBid, secondAsk, quantity);
            strategy =
                    new CrossVenueBasisStrategy(
                            StrategyTestFixtures.linearDefinition(),
                            firstInstrument,
                            secondInstrument,
                            StrategyTestFixtures.registry(),
                            0,
                            2);
        }

        private PricingStatus evaluate(final BasisDirection direction, final long exposure) {
            return strategy.evaluate(
                    direction,
                    firstBook,
                    secondBook,
                    StrategyTestFixtures.inputs(1_000),
                    exposure,
                    0,
                    7,
                    StrategyTestFixtures.DECISION_MONO,
                    StrategyTestFixtures.DECISION_EPOCH,
                    result);
        }
    }
}
