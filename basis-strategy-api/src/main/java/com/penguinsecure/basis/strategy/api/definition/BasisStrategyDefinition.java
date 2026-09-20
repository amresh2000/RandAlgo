package com.penguinsecure.basis.strategy.api.definition;

import com.penguinsecure.basis.core.numeric.RoundingPolicy;

/** Immutable definition of one certifiable cross-venue basis strategy version. */
public record BasisStrategyDefinition(
        int strategyId,
        int versionMajor,
        int versionMinor,
        int versionPatch,
        StrategyLifecycle lifecycle,
        long effectiveEpochNanos,
        long configurationHashHigh,
        long configurationHashLow,
        StrategyLegDefinition firstLeg,
        StrategyLegDefinition secondLeg,
        int underlyingCurrencyId,
        int riskCurrencyId,
        StrategyModelIds modelIds,
        EconomicSourceIds economicSourceIds,
        RoundingPolicy hedgeRounding,
        long entryThreshold,
        long exitThreshold,
        long holdingHorizonNanos,
        long opportunityExpiryNanos,
        long maximumReceiveSkewNanos,
        long marketDataToWriteP99Nanos,
        long marketDataToWriteP999Nanos,
        long fillToHedgeWriteP99Nanos,
        long fillToHedgeWriteP999Nanos,
        StrategyRiskLimits riskLimits) {}
