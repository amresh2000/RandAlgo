package com.penguinsecure.basis.strategy.api.pricing;

/** Complete immutable economic evidence bundle required by basis pricing. */
public record EconomicInputs(
        FeeSchedule firstFees,
        FeeSchedule secondFees,
        DirectionalRateSchedule firstFunding,
        DirectionalRateSchedule secondFunding,
        DirectionalRateSchedule settlementCarry,
        ConversionRate firstConversion,
        ConversionRate secondConversion,
        EconomicRate conversionCost,
        EconomicRate slippage,
        EconomicRate safetyReserve,
        LiquidityRiskTable liquidityRisk) {
    public EconomicInputs {
        if (firstFees == null
                || secondFees == null
                || firstFunding == null
                || secondFunding == null
                || settlementCarry == null
                || firstConversion == null
                || secondConversion == null
                || conversionCost == null
                || slippage == null
                || safetyReserve == null
                || liquidityRisk == null) {
            throw new NullPointerException("all economic inputs are mandatory");
        }
    }
}
