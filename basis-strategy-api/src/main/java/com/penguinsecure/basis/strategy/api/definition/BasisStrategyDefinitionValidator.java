package com.penguinsecure.basis.strategy.api.definition;

import com.penguinsecure.basis.strategy.api.model.StrategyModelRegistry;

/** Fail-closed structural validation before a definition enters the runtime catalog. */
public final class BasisStrategyDefinitionValidator {
    private BasisStrategyDefinitionValidator() {}

    public static StrategyValidationStatus validate(final BasisStrategyDefinition definition) {
        if (definition == null || definition.strategyId() <= 0) {
            return StrategyValidationStatus.INVALID_IDENTITY;
        }
        if (definition.versionMajor() < 0
                || definition.versionMinor() < 0
                || definition.versionPatch() < 0) {
            return StrategyValidationStatus.INVALID_VERSION;
        }
        if (definition.lifecycle() == null) return StrategyValidationStatus.INVALID_LIFECYCLE;
        if (definition.effectiveEpochNanos() <= 0) {
            return StrategyValidationStatus.INVALID_EFFECTIVE_TIME;
        }
        if (!validLeg(definition.firstLeg()) || !validLeg(definition.secondLeg())) {
            return StrategyValidationStatus.INVALID_LEG;
        }
        if (definition.firstLeg().venueId() == definition.secondLeg().venueId()
                && definition.firstLeg().instrumentId() == definition.secondLeg().instrumentId()) {
            return StrategyValidationStatus.DUPLICATE_LEG;
        }
        if (definition.underlyingCurrencyId() <= 0 || definition.riskCurrencyId() <= 0) {
            return StrategyValidationStatus.INVALID_CURRENCY;
        }
        if (!validModels(definition.modelIds())) return StrategyValidationStatus.INVALID_MODEL_ID;
        if (!validSources(definition.economicSourceIds())) {
            return StrategyValidationStatus.INVALID_ECONOMIC_SOURCE;
        }
        if (definition.hedgeRounding() == null
                || definition.entryThreshold() <= definition.exitThreshold()) {
            return StrategyValidationStatus.INVALID_THRESHOLD;
        }
        if (definition.holdingHorizonNanos() <= 0
                || definition.opportunityExpiryNanos() <= 0
                || definition.maximumReceiveSkewNanos() <= 0) {
            return StrategyValidationStatus.INVALID_TIME_LIMIT;
        }
        if (definition.marketDataToWriteP99Nanos() <= 0
                || definition.marketDataToWriteP999Nanos() < definition.marketDataToWriteP99Nanos()
                || definition.fillToHedgeWriteP99Nanos() <= 0
                || definition.fillToHedgeWriteP999Nanos() < definition.fillToHedgeWriteP99Nanos()) {
            return StrategyValidationStatus.INVALID_PERFORMANCE_BUDGET;
        }
        return validRisk(definition.riskLimits())
                ? StrategyValidationStatus.VALID
                : StrategyValidationStatus.INVALID_RISK_LIMIT;
    }

    public static StrategyValidationStatus validate(
            final BasisStrategyDefinition definition, final StrategyModelRegistry registry) {
        final StrategyValidationStatus structural = validate(definition);
        if (structural != StrategyValidationStatus.VALID) return structural;
        if (registry == null || !registry.frozen())
            return StrategyValidationStatus.INVALID_MODEL_ID;
        final StrategyModelIds models = definition.modelIds();
        return registry.payoff(models.firstPayoffModelId()) != null
                        && registry.payoff(models.secondPayoffModelId()) != null
                        && registry.hedgeRatio(models.hedgeRatioModelId()) != null
                        && registry.carry(models.carryModelId()) != null
                        && registry.signal(models.signalModelId()) != null
                        && registry.executionPolicy(models.executionPolicyId()) != null
                ? StrategyValidationStatus.VALID
                : StrategyValidationStatus.INVALID_MODEL_ID;
    }

    private static boolean validLeg(final StrategyLegDefinition leg) {
        return leg != null
                && leg.venueId() > 0
                && leg.instrumentId() > 0
                && leg.accountId() > 0
                && leg.feedProfileId() > 0
                && leg.maximumAgeNanos() > 0;
    }

    private static boolean validModels(final StrategyModelIds models) {
        return models != null
                && models.firstPayoffModelId() > 0
                && models.secondPayoffModelId() > 0
                && models.hedgeRatioModelId() > 0
                && models.carryModelId() > 0
                && models.signalModelId() > 0
                && models.executionPolicyId() > 0;
    }

    private static boolean validSources(final EconomicSourceIds sources) {
        return sources != null
                && sources.feeSourceId() > 0
                && sources.fundingSourceId() > 0
                && sources.conversionSourceId() > 0
                && sources.liquidityHaircutModelId() > 0
                && sources.slippageModelId() > 0
                && sources.latencyRiskModelId() > 0
                && sources.safetyReserveModelId() > 0;
    }

    private static boolean validRisk(final StrategyRiskLimits risk) {
        return risk != null
                && risk.maximumGrossExposure() > 0
                && risk.maximumNetExposure() > 0
                && risk.maximumUnhedgedExposure() > 0
                && risk.maximumImbalance() > 0
                && risk.capitalLimit() > 0
                && risk.collateralLimit() > 0
                && risk.maximumConcurrentGroups() > 0;
    }
}
