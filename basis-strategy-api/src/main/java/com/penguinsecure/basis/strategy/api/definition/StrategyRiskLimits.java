package com.penguinsecure.basis.strategy.api.definition;

/** Exact scaled risk and capacity limits for one strategy generation. */
public record StrategyRiskLimits(
        long maximumGrossExposure,
        long maximumNetExposure,
        long maximumUnhedgedExposure,
        long maximumImbalance,
        long capitalLimit,
        long collateralLimit,
        int maximumConcurrentGroups) {}
