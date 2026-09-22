package com.penguinsecure.basis.core.risk;

/** Immutable generation-fenced capacity authorized for one strategy instance. */
public record RiskEnvelope(
        int strategyId,
        long configurationGeneration,
        long envelopeGeneration,
        long expiryMonoNanos,
        long maximumGrossExposure,
        long maximumNetExposure,
        long maximumUnhedgedExposure,
        long maximumPosition,
        long maximumCollateral,
        long maximumDailyLoss,
        long maximumImbalance,
        long maximumPriceDeviationTicks,
        int maximumConcurrentGroups) {
    public RiskEnvelope {
        if (strategyId <= 0
                || configurationGeneration <= 0
                || envelopeGeneration <= 0
                || expiryMonoNanos <= 0
                || maximumGrossExposure <= 0
                || maximumNetExposure <= 0
                || maximumUnhedgedExposure <= 0
                || maximumPosition <= 0
                || maximumCollateral <= 0
                || maximumDailyLoss <= 0
                || maximumImbalance <= 0
                || maximumPriceDeviationTicks < 0
                || maximumConcurrentGroups <= 0) {
            throw new IllegalArgumentException("invalid risk envelope");
        }
    }
}
