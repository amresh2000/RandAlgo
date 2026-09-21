package com.penguinsecure.basis.core.oems;

import com.penguinsecure.basis.core.command.OrderSide;

/** Native aggressive two-leg IOC plan produced after opportunity sizing. */
public record AggressiveExecutionPlan(
        int cellId,
        int strategySlot,
        int strategyId,
        long configurationGeneration,
        int initiationVenueId,
        int hedgeVenueId,
        long initiationSessionGeneration,
        long hedgeSessionGeneration,
        int initiationInstrumentId,
        int hedgeInstrumentId,
        OrderSide initiationSide,
        OrderSide hedgeSide,
        long initiationQuantity,
        long hedgeQuantity,
        long initiationLimitPriceTicks,
        long hedgeLimitPriceTicks,
        long maximumImbalance,
        long deadlineMonoNanos) {
    public AggressiveExecutionPlan {
        if (cellId < 0
                || strategySlot < 0
                || strategyId <= 0
                || configurationGeneration <= 0
                || initiationVenueId < 0
                || hedgeVenueId < 0
                || initiationSessionGeneration <= 0
                || hedgeSessionGeneration <= 0
                || initiationInstrumentId < 0
                || hedgeInstrumentId < 0
                || initiationSide == null
                || hedgeSide == null
                || initiationQuantity <= 0
                || hedgeQuantity <= 0
                || initiationLimitPriceTicks <= 0
                || hedgeLimitPriceTicks <= 0
                || maximumImbalance <= 0
                || deadlineMonoNanos <= 0) {
            throw new IllegalArgumentException("invalid execution plan");
        }
    }
}
