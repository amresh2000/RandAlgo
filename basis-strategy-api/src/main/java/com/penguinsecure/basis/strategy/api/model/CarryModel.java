package com.penguinsecure.basis.strategy.api.model;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.strategy.api.pricing.DirectionalRateSchedule;

/** Compiled funding and dated-settlement carry calculation. */
public interface CarryModel {
    int modelId();

    NumericStatus fundingCost(
            long firstAmount,
            DirectionalRateSchedule firstFunding,
            boolean firstBuy,
            long secondAmount,
            DirectionalRateSchedule secondFunding,
            boolean secondBuy,
            int amountScale,
            MutableLongResult scratch,
            MutableLongResult result);

    NumericStatus settlementCost(
            long firstAmount,
            long secondAmount,
            DirectionalRateSchedule settlement,
            boolean firstBuy,
            int amountScale,
            MutableLongResult scratch,
            MutableLongResult result);
}
