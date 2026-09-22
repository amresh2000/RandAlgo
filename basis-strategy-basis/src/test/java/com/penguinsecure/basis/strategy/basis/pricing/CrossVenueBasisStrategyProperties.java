package com.penguinsecure.basis.strategy.basis.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.strategy.api.pricing.BasisDirection;
import com.penguinsecure.basis.strategy.api.pricing.MutableBasisOpportunity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;

@Tag("property")
final class CrossVenueBasisStrategyProperties {
    @Property(tries = 500)
    void fixedPointNetEdgeMatchesBigDecimalOracle(
            @ForAll @IntRange(min = 5_000, max = 20_000) int buyPrice,
            @ForAll @IntRange(min = 1, max = 500) int spread,
            @ForAll @IntRange(min = 1, max = 100) int quantity) {
        long sellPrice = buyPrice + spread;
        InstrumentDefinition firstInstrument = StrategyTestFixtures.linearInstrument(1, 101);
        InstrumentDefinition secondInstrument = StrategyTestFixtures.linearInstrument(2, 201);
        FixedDepthOrderBook firstBook =
                StrategyTestFixtures.book(1, 101, 1, 1_000_000, buyPrice - 1L, buyPrice, 1_000);
        FixedDepthOrderBook secondBook =
                StrategyTestFixtures.book(2, 201, 2, 1_000_010, sellPrice, sellPrice + 1, 1_000);
        CrossVenueBasisStrategy strategy =
                new CrossVenueBasisStrategy(
                        StrategyTestFixtures.linearDefinition(),
                        firstInstrument,
                        secondInstrument,
                        StrategyTestFixtures.registry(),
                        0,
                        2);
        MutableBasisOpportunity result = new MutableBasisOpportunity();

        strategy.evaluate(
                BasisDirection.BUY_FIRST_SELL_SECOND,
                firstBook,
                secondBook,
                StrategyTestFixtures.inputs(1_000),
                quantity,
                0,
                7,
                StrategyTestFixtures.DECISION_MONO,
                StrategyTestFixtures.DECISION_EPOCH,
                result);

        BigDecimal buy = BigDecimal.valueOf(buyPrice).multiply(BigDecimal.valueOf(quantity));
        BigDecimal sell = BigDecimal.valueOf(sellPrice).multiply(BigDecimal.valueOf(quantity));
        BigDecimal feeRate = new BigDecimal("0.001");
        long expected =
                sell.subtract(buy)
                        .subtract(buy.multiply(feeRate).setScale(0, RoundingMode.CEILING))
                        .subtract(sell.multiply(feeRate).setScale(0, RoundingMode.CEILING))
                        .longValueExact();
        assertEquals(expected, result.netEdge());
    }
}
