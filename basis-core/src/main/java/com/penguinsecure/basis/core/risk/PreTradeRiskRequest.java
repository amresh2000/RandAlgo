package com.penguinsecure.basis.core.risk;

/** Caller-owned primitive request combining opportunity evidence with current core state. */
public final class PreTradeRiskRequest {
    int strategySlot;
    int strategyId;
    long configurationGeneration;
    long envelopeGeneration;
    int firstVenueId;
    int secondVenueId;
    int firstAccountId;
    int secondAccountId;
    int firstInstrumentId;
    int secondInstrumentId;
    int groupScopeId;
    int sessionScopeId;
    long expectedFirstSessionGeneration;
    long expectedSecondSessionGeneration;
    long currentFirstSessionGeneration;
    long currentSecondSessionGeneration;
    boolean sessionsHealthy;
    long firstBookEpoch;
    long firstBookSequence;
    long secondBookEpoch;
    long secondBookSequence;
    long firstReceiveMonoNanos;
    long secondReceiveMonoNanos;
    long firstMaximumAgeNanos;
    long secondMaximumAgeNanos;
    long maximumSkewNanos;
    long opportunityExpiryMonoNanos;
    long currentFirstBookEpoch;
    long currentFirstBookSequence;
    long currentSecondBookEpoch;
    long currentSecondBookSequence;
    long currentFirstReceiveMonoNanos;
    long currentSecondReceiveMonoNanos;
    boolean currentFirstBookTrusted;
    boolean currentSecondBookTrusted;
    long nowMonoNanos;
    long firstNativeQuantity;
    long secondNativeQuantity;
    long firstWorstPriceTicks;
    long secondWorstPriceTicks;
    long firstReferencePriceTicks;
    long secondReferencePriceTicks;
    long firstTickSize;
    long secondTickSize;
    long firstLotSize;
    long secondLotSize;
    long firstMinimumQuantity;
    long secondMinimumQuantity;
    long firstMaximumQuantity;
    long secondMaximumQuantity;
    long requestedGrossExposure;
    long requestedNetExposure;
    long requestedUnhedgedExposure;
    long requestedCollateral;
    long hedgeLiquidity;
    long requestedMaximumImbalance;
    long hedgeRateClaims;
    long worstExposureBefore;
    long worstExposureAfter;
    boolean duplicateOrRunaway;
    boolean riskReducing;
    PartitionedTokenBucket rateCapacity;
    HedgePathHealth hedgePathHealth;

    public PreTradeRiskRequest identity(
            final int newStrategySlot,
            final int newStrategyId,
            final long newConfigurationGeneration,
            final long newEnvelopeGeneration,
            final int newGroupScopeId,
            final int newSessionScopeId) {
        strategySlot = newStrategySlot;
        strategyId = newStrategyId;
        configurationGeneration = newConfigurationGeneration;
        envelopeGeneration = newEnvelopeGeneration;
        groupScopeId = newGroupScopeId;
        sessionScopeId = newSessionScopeId;
        return this;
    }

    @SuppressWarnings("ParameterNumber")
    public PreTradeRiskRequest routes(
            final int newFirstVenueId,
            final int newSecondVenueId,
            final int newFirstAccountId,
            final int newSecondAccountId,
            final int newFirstInstrumentId,
            final int newSecondInstrumentId,
            final long newExpectedFirstSessionGeneration,
            final long newExpectedSecondSessionGeneration,
            final long newCurrentFirstSessionGeneration,
            final long newCurrentSecondSessionGeneration,
            final boolean newSessionsHealthy) {
        firstVenueId = newFirstVenueId;
        secondVenueId = newSecondVenueId;
        firstAccountId = newFirstAccountId;
        secondAccountId = newSecondAccountId;
        firstInstrumentId = newFirstInstrumentId;
        secondInstrumentId = newSecondInstrumentId;
        expectedFirstSessionGeneration = newExpectedFirstSessionGeneration;
        expectedSecondSessionGeneration = newExpectedSecondSessionGeneration;
        currentFirstSessionGeneration = newCurrentFirstSessionGeneration;
        currentSecondSessionGeneration = newCurrentSecondSessionGeneration;
        sessionsHealthy = newSessionsHealthy;
        return this;
    }

    @SuppressWarnings("ParameterNumber")
    public PreTradeRiskRequest opportunityEvidence(
            final long newFirstBookEpoch,
            final long newFirstBookSequence,
            final long newSecondBookEpoch,
            final long newSecondBookSequence,
            final long newFirstReceiveMonoNanos,
            final long newSecondReceiveMonoNanos,
            final long newFirstMaximumAgeNanos,
            final long newSecondMaximumAgeNanos,
            final long newMaximumSkewNanos,
            final long newOpportunityExpiryMonoNanos) {
        firstBookEpoch = newFirstBookEpoch;
        firstBookSequence = newFirstBookSequence;
        secondBookEpoch = newSecondBookEpoch;
        secondBookSequence = newSecondBookSequence;
        firstReceiveMonoNanos = newFirstReceiveMonoNanos;
        secondReceiveMonoNanos = newSecondReceiveMonoNanos;
        firstMaximumAgeNanos = newFirstMaximumAgeNanos;
        secondMaximumAgeNanos = newSecondMaximumAgeNanos;
        maximumSkewNanos = newMaximumSkewNanos;
        opportunityExpiryMonoNanos = newOpportunityExpiryMonoNanos;
        return this;
    }

    @SuppressWarnings("ParameterNumber")
    public PreTradeRiskRequest currentEvidence(
            final long newCurrentFirstBookEpoch,
            final long newCurrentFirstBookSequence,
            final long newCurrentSecondBookEpoch,
            final long newCurrentSecondBookSequence,
            final long newCurrentFirstReceiveMonoNanos,
            final long newCurrentSecondReceiveMonoNanos,
            final boolean newCurrentFirstBookTrusted,
            final boolean newCurrentSecondBookTrusted,
            final long newNowMonoNanos) {
        currentFirstBookEpoch = newCurrentFirstBookEpoch;
        currentFirstBookSequence = newCurrentFirstBookSequence;
        currentSecondBookEpoch = newCurrentSecondBookEpoch;
        currentSecondBookSequence = newCurrentSecondBookSequence;
        currentFirstReceiveMonoNanos = newCurrentFirstReceiveMonoNanos;
        currentSecondReceiveMonoNanos = newCurrentSecondReceiveMonoNanos;
        currentFirstBookTrusted = newCurrentFirstBookTrusted;
        currentSecondBookTrusted = newCurrentSecondBookTrusted;
        nowMonoNanos = newNowMonoNanos;
        return this;
    }

    @SuppressWarnings("ParameterNumber")
    public PreTradeRiskRequest nativeOrders(
            final long newFirstNativeQuantity,
            final long newSecondNativeQuantity,
            final long newFirstWorstPriceTicks,
            final long newSecondWorstPriceTicks,
            final long newFirstReferencePriceTicks,
            final long newSecondReferencePriceTicks,
            final long newFirstTickSize,
            final long newSecondTickSize,
            final long newFirstLotSize,
            final long newSecondLotSize,
            final long newFirstMinimumQuantity,
            final long newSecondMinimumQuantity,
            final long newFirstMaximumQuantity,
            final long newSecondMaximumQuantity) {
        firstNativeQuantity = newFirstNativeQuantity;
        secondNativeQuantity = newSecondNativeQuantity;
        firstWorstPriceTicks = newFirstWorstPriceTicks;
        secondWorstPriceTicks = newSecondWorstPriceTicks;
        firstReferencePriceTicks = newFirstReferencePriceTicks;
        secondReferencePriceTicks = newSecondReferencePriceTicks;
        firstTickSize = newFirstTickSize;
        secondTickSize = newSecondTickSize;
        firstLotSize = newFirstLotSize;
        secondLotSize = newSecondLotSize;
        firstMinimumQuantity = newFirstMinimumQuantity;
        secondMinimumQuantity = newSecondMinimumQuantity;
        firstMaximumQuantity = newFirstMaximumQuantity;
        secondMaximumQuantity = newSecondMaximumQuantity;
        return this;
    }

    @SuppressWarnings("ParameterNumber")
    public PreTradeRiskRequest exposure(
            final long newRequestedGrossExposure,
            final long newRequestedNetExposure,
            final long newRequestedUnhedgedExposure,
            final long newRequestedCollateral,
            final long newHedgeLiquidity,
            final long newRequestedMaximumImbalance,
            final long newHedgeRateClaims,
            final long newWorstExposureBefore,
            final long newWorstExposureAfter,
            final boolean newDuplicateOrRunaway,
            final boolean newRiskReducing) {
        requestedGrossExposure = newRequestedGrossExposure;
        requestedNetExposure = newRequestedNetExposure;
        requestedUnhedgedExposure = newRequestedUnhedgedExposure;
        requestedCollateral = newRequestedCollateral;
        hedgeLiquidity = newHedgeLiquidity;
        requestedMaximumImbalance = newRequestedMaximumImbalance;
        hedgeRateClaims = newHedgeRateClaims;
        worstExposureBefore = newWorstExposureBefore;
        worstExposureAfter = newWorstExposureAfter;
        duplicateOrRunaway = newDuplicateOrRunaway;
        riskReducing = newRiskReducing;
        return this;
    }

    public PreTradeRiskRequest safety(
            final PartitionedTokenBucket newRateCapacity,
            final HedgePathHealth newHedgePathHealth) {
        rateCapacity = newRateCapacity;
        hedgePathHealth = newHedgePathHealth;
        return this;
    }

    @SuppressWarnings("ParameterNumber")
    public boolean matchesExecutionPlan(
            final int planStrategySlot,
            final int planStrategyId,
            final long planConfigurationGeneration,
            final int initiationVenueId,
            final int hedgeVenueId,
            final long initiationSessionGeneration,
            final long hedgeSessionGeneration,
            final int initiationInstrumentId,
            final int hedgeInstrumentId,
            final long initiationQuantity,
            final long hedgeQuantity,
            final long initiationPrice,
            final long hedgePrice,
            final long deadline) {
        if (planStrategySlot != strategySlot
                || planStrategyId != strategyId
                || planConfigurationGeneration != configurationGeneration
                || deadline > opportunityExpiryMonoNanos) return false;
        final boolean direct =
                initiationVenueId == firstVenueId
                        && hedgeVenueId == secondVenueId
                        && initiationSessionGeneration == expectedFirstSessionGeneration
                        && hedgeSessionGeneration == expectedSecondSessionGeneration
                        && initiationInstrumentId == firstInstrumentId
                        && hedgeInstrumentId == secondInstrumentId
                        && initiationQuantity == firstNativeQuantity
                        && hedgeQuantity == secondNativeQuantity
                        && initiationPrice == firstWorstPriceTicks
                        && hedgePrice == secondWorstPriceTicks;
        final boolean reverse =
                initiationVenueId == secondVenueId
                        && hedgeVenueId == firstVenueId
                        && initiationSessionGeneration == expectedSecondSessionGeneration
                        && hedgeSessionGeneration == expectedFirstSessionGeneration
                        && initiationInstrumentId == secondInstrumentId
                        && hedgeInstrumentId == firstInstrumentId
                        && initiationQuantity == secondNativeQuantity
                        && hedgeQuantity == firstNativeQuantity
                        && initiationPrice == secondWorstPriceTicks
                        && hedgePrice == firstWorstPriceTicks;
        return direct || reverse;
    }
}
