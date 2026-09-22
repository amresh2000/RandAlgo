package com.penguinsecure.basis.strategy.basis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.strategy.api.pricing.DirectionalRateSchedule;
import com.penguinsecure.basis.strategy.api.pricing.InputMetadata;
import org.junit.jupiter.api.Test;

final class FundingSettlementCarryModelTest {
    private static final InputMetadata METADATA = new InputMetadata(1, 1, 1, 1, 1_000, 1_000, 1, 1);

    @Test
    void appliesDirectionSpecificRatesToBothLegs() {
        FundingSettlementCarryModel model = new FundingSettlementCarryModel(1);
        MutableLongResult scratch = new MutableLongResult();
        MutableLongResult result = new MutableLongResult();

        assertEquals(
                NumericStatus.OK,
                model.fundingCost(
                        10_000,
                        new DirectionalRateSchedule(3, 7, 2, METADATA),
                        true,
                        20_000,
                        new DirectionalRateSchedule(5, 11, 2, METADATA),
                        false,
                        2,
                        scratch,
                        result));
        assertEquals(2_500, result.value());

        assertEquals(
                NumericStatus.OK,
                model.settlementCost(
                        10_000,
                        20_000,
                        new DirectionalRateSchedule(3, 4, 2, METADATA),
                        true,
                        2,
                        scratch,
                        result));
        assertEquals(1_100, result.value());
    }
}
