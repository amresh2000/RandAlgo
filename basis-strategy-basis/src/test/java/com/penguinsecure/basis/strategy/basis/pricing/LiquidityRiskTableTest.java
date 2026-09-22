package com.penguinsecure.basis.strategy.basis.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penguinsecure.basis.strategy.api.pricing.BasisDirection;
import com.penguinsecure.basis.strategy.api.pricing.LiquidityRiskTable;
import org.junit.jupiter.api.Test;

final class LiquidityRiskTableTest {
    @Test
    void selectsTheSmallestApplicableSizeTier() {
        LiquidityRiskTable table =
                table(3).add(BasisDirection.BUY_FIRST_SELL_SECOND, 1, 2, 1_000, 0, 10, 20)
                        .add(BasisDirection.BUY_FIRST_SELL_SECOND, 1, 2, 100, 0, 30, 40)
                        .freeze();

        assertEquals(1, table.find(BasisDirection.BUY_FIRST_SELL_SECOND, 1, 2, 50, 0));
        assertEquals(0, table.find(BasisDirection.BUY_FIRST_SELL_SECOND, 1, 2, 101, 0));
        assertEquals(-1, table.find(BasisDirection.SELL_FIRST_BUY_SECOND, 1, 2, 50, 0));
    }

    @Test
    void rejectsDuplicateKeysAndMutationAfterFreeze() {
        LiquidityRiskTable table =
                table(2).add(BasisDirection.BUY_FIRST_SELL_SECOND, 1, 2, 100, 0, 10, 20);

        assertThrows(
                IllegalStateException.class,
                () -> table.add(BasisDirection.BUY_FIRST_SELL_SECOND, 1, 2, 100, 0, 30, 40));
        table.freeze();
        assertThrows(
                IllegalStateException.class,
                () -> table.add(BasisDirection.BUY_FIRST_SELL_SECOND, 1, 2, 1_000, 0, 30, 40));
    }

    private static LiquidityRiskTable table(final int capacity) {
        return new LiquidityRiskTable(
                1, 2, 8, StrategyTestFixtures.metadata(99, 1, 1_000), capacity);
    }
}
