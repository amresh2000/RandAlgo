package com.penguinsecure.basis.strategy.basis.model;

import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.PayoffMath;
import com.penguinsecure.basis.strategy.api.model.CarryModel;
import com.penguinsecure.basis.strategy.api.pricing.DirectionalRateSchedule;

/** Reusable direction-aware funding plus dated settlement carry model. */
public final class FundingSettlementCarryModel implements CarryModel {
    private final int modelId;

    public FundingSettlementCarryModel(final int modelId) {
        if (modelId <= 0) throw new IllegalArgumentException("modelId must be positive");
        this.modelId = modelId;
    }

    @Override
    public int modelId() {
        return modelId;
    }

    @Override
    public NumericStatus fundingCost(
            final long firstAmount,
            final DirectionalRateSchedule firstFunding,
            final boolean firstBuy,
            final long secondAmount,
            final DirectionalRateSchedule secondFunding,
            final boolean secondBuy,
            final int amountScale,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        return twoLegCost(
                firstAmount,
                firstFunding,
                firstBuy,
                secondAmount,
                secondFunding,
                secondBuy,
                amountScale,
                scratch,
                result);
    }

    @Override
    public NumericStatus settlementCost(
            final long firstAmount,
            final long secondAmount,
            final DirectionalRateSchedule settlement,
            final boolean firstBuy,
            final int amountScale,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        return twoLegCost(
                firstAmount,
                settlement,
                firstBuy,
                secondAmount,
                settlement,
                !firstBuy,
                amountScale,
                scratch,
                result);
    }

    private static NumericStatus twoLegCost(
            final long firstAmount,
            final DirectionalRateSchedule firstRate,
            final boolean firstBuy,
            final long secondAmount,
            final DirectionalRateSchedule secondRate,
            final boolean secondBuy,
            final int amountScale,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (rateCost(
                        firstAmount,
                        firstRate.rate(firstBuy),
                        firstRate.scale(),
                        amountScale,
                        scratch,
                        result)
                != NumericStatus.OK) return result.status();
        final long firstCost = result.value();
        if (rateCost(
                        secondAmount,
                        secondRate.rate(secondBuy),
                        secondRate.scale(),
                        amountScale,
                        scratch,
                        result)
                != NumericStatus.OK) return result.status();
        return CheckedDecimalMath.add(firstCost, result.value(), result);
    }

    private static NumericStatus rateCost(
            final long amount,
            final long rate,
            final int rateScale,
            final int amountScale,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        return PayoffMath.rateAmount(
                amount,
                amountScale,
                rate,
                rateScale,
                amountScale,
                RoundingPolicy.CEILING,
                scratch,
                result);
    }
}
