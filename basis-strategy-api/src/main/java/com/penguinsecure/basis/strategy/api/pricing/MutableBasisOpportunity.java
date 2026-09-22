package com.penguinsecure.basis.strategy.api.pricing;

/** Caller-owned complete economic decision and evidence tuple. */
public final class MutableBasisOpportunity {
    private PricingStatus status = PricingStatus.INVALID_ARGUMENT;
    private BasisDirection direction;
    private int strategyId;
    private long configurationGeneration;
    private long configurationHashHigh;
    private long configurationHashLow;
    private long firstBookEpoch;
    private long firstBookSequence;
    private long secondBookEpoch;
    private long secondBookSequence;
    private int firstFeedProfileId;
    private int secondFeedProfileId;
    private long firstReceiveMonoNanos;
    private long secondReceiveMonoNanos;
    private long firstAgeNanos;
    private long secondAgeNanos;
    private long receiveSkewNanos;
    private long firstMaximumAgeNanos;
    private long secondMaximumAgeNanos;
    private long maximumReceiveSkewNanos;
    private long decisionMonoNanos;
    private long decisionEpochNanos;
    private long expiryMonoNanos;
    private long maximumCanonicalExposure;
    private long canonicalExposure;
    private long firstNativeQuantity;
    private long secondNativeQuantity;
    private long firstAveragePrice;
    private long secondAveragePrice;
    private long firstWorstPrice;
    private long secondWorstPrice;
    private long grossProceeds;
    private long grossCost;
    private long firstFee;
    private long secondFee;
    private long fundingCost;
    private long settlementCost;
    private long conversionCost;
    private long slippageCost;
    private long latencyRiskCost;
    private long safetyReserveCost;
    private long netEdge;
    private long netEdgeRate;
    private int edgeRateScale;
    private long haircutRate;
    private long latencyRiskRate;
    private int riskRateScale;
    private long firstFeeGeneration;
    private long secondFeeGeneration;
    private long firstFundingGeneration;
    private long secondFundingGeneration;
    private long settlementGeneration;
    private long firstConversionGeneration;
    private long secondConversionGeneration;
    private long conversionCostGeneration;
    private long slippageGeneration;
    private long safetyReserveGeneration;
    private long liquidityRiskGeneration;

    public void reject(final PricingStatus newStatus, final BasisDirection newDirection) {
        if (newStatus == null) throw new NullPointerException("status is required");
        status = newStatus;
        direction = newDirection;
        strategyId = 0;
        configurationGeneration = 0;
        configurationHashHigh = 0;
        configurationHashLow = 0;
        firstBookEpoch = 0;
        firstBookSequence = 0;
        secondBookEpoch = 0;
        secondBookSequence = 0;
        firstFeedProfileId = 0;
        secondFeedProfileId = 0;
        firstReceiveMonoNanos = 0;
        secondReceiveMonoNanos = 0;
        firstAgeNanos = 0;
        secondAgeNanos = 0;
        receiveSkewNanos = 0;
        firstMaximumAgeNanos = 0;
        secondMaximumAgeNanos = 0;
        maximumReceiveSkewNanos = 0;
        decisionMonoNanos = 0;
        decisionEpochNanos = 0;
        expiryMonoNanos = 0;
        maximumCanonicalExposure = 0;
        canonicalExposure = 0;
        firstNativeQuantity = 0;
        secondNativeQuantity = 0;
        firstAveragePrice = 0;
        secondAveragePrice = 0;
        firstWorstPrice = 0;
        secondWorstPrice = 0;
        grossProceeds = 0;
        grossCost = 0;
        firstFee = 0;
        secondFee = 0;
        fundingCost = 0;
        settlementCost = 0;
        conversionCost = 0;
        slippageCost = 0;
        latencyRiskCost = 0;
        safetyReserveCost = 0;
        netEdge = 0;
        netEdgeRate = 0;
        edgeRateScale = 0;
        haircutRate = 0;
        latencyRiskRate = 0;
        riskRateScale = 0;
        firstFeeGeneration = 0;
        secondFeeGeneration = 0;
        firstFundingGeneration = 0;
        secondFundingGeneration = 0;
        settlementGeneration = 0;
        firstConversionGeneration = 0;
        secondConversionGeneration = 0;
        conversionCostGeneration = 0;
        slippageGeneration = 0;
        safetyReserveGeneration = 0;
        liquidityRiskGeneration = 0;
    }

    @SuppressWarnings("ParameterNumber")
    public void set(
            final PricingStatus newStatus,
            final BasisDirection newDirection,
            final int newStrategyId,
            final long newConfigurationGeneration,
            final long newConfigurationHashHigh,
            final long newConfigurationHashLow,
            final long newFirstBookEpoch,
            final long newFirstBookSequence,
            final long newSecondBookEpoch,
            final long newSecondBookSequence,
            final int newFirstFeedProfileId,
            final int newSecondFeedProfileId,
            final long newFirstReceiveMonoNanos,
            final long newSecondReceiveMonoNanos,
            final long newFirstAgeNanos,
            final long newSecondAgeNanos,
            final long newReceiveSkewNanos,
            final long newFirstMaximumAgeNanos,
            final long newSecondMaximumAgeNanos,
            final long newMaximumReceiveSkewNanos,
            final long newDecisionMonoNanos,
            final long newDecisionEpochNanos,
            final long newExpiryMonoNanos,
            final long newMaximumCanonicalExposure,
            final long newCanonicalExposure,
            final long newFirstNativeQuantity,
            final long newSecondNativeQuantity,
            final long newFirstAveragePrice,
            final long newSecondAveragePrice,
            final long newFirstWorstPrice,
            final long newSecondWorstPrice,
            final long newGrossProceeds,
            final long newGrossCost,
            final long newFirstFee,
            final long newSecondFee,
            final long newFundingCost,
            final long newSettlementCost,
            final long newConversionCost,
            final long newSlippageCost,
            final long newLatencyRiskCost,
            final long newSafetyReserveCost,
            final long newNetEdge,
            final long newNetEdgeRate,
            final int newEdgeRateScale,
            final long newHaircutRate,
            final long newLatencyRiskRate,
            final int newRiskRateScale,
            final long newFirstFeeGeneration,
            final long newSecondFeeGeneration,
            final long newFirstFundingGeneration,
            final long newSecondFundingGeneration,
            final long newSettlementGeneration,
            final long newFirstConversionGeneration,
            final long newSecondConversionGeneration,
            final long newConversionCostGeneration,
            final long newSlippageGeneration,
            final long newSafetyReserveGeneration,
            final long newLiquidityRiskGeneration) {
        status = newStatus;
        direction = newDirection;
        strategyId = newStrategyId;
        configurationGeneration = newConfigurationGeneration;
        configurationHashHigh = newConfigurationHashHigh;
        configurationHashLow = newConfigurationHashLow;
        firstBookEpoch = newFirstBookEpoch;
        firstBookSequence = newFirstBookSequence;
        secondBookEpoch = newSecondBookEpoch;
        secondBookSequence = newSecondBookSequence;
        firstFeedProfileId = newFirstFeedProfileId;
        secondFeedProfileId = newSecondFeedProfileId;
        firstReceiveMonoNanos = newFirstReceiveMonoNanos;
        secondReceiveMonoNanos = newSecondReceiveMonoNanos;
        firstAgeNanos = newFirstAgeNanos;
        secondAgeNanos = newSecondAgeNanos;
        receiveSkewNanos = newReceiveSkewNanos;
        firstMaximumAgeNanos = newFirstMaximumAgeNanos;
        secondMaximumAgeNanos = newSecondMaximumAgeNanos;
        maximumReceiveSkewNanos = newMaximumReceiveSkewNanos;
        decisionMonoNanos = newDecisionMonoNanos;
        decisionEpochNanos = newDecisionEpochNanos;
        expiryMonoNanos = newExpiryMonoNanos;
        maximumCanonicalExposure = newMaximumCanonicalExposure;
        canonicalExposure = newCanonicalExposure;
        firstNativeQuantity = newFirstNativeQuantity;
        secondNativeQuantity = newSecondNativeQuantity;
        firstAveragePrice = newFirstAveragePrice;
        secondAveragePrice = newSecondAveragePrice;
        firstWorstPrice = newFirstWorstPrice;
        secondWorstPrice = newSecondWorstPrice;
        grossProceeds = newGrossProceeds;
        grossCost = newGrossCost;
        firstFee = newFirstFee;
        secondFee = newSecondFee;
        fundingCost = newFundingCost;
        settlementCost = newSettlementCost;
        conversionCost = newConversionCost;
        slippageCost = newSlippageCost;
        latencyRiskCost = newLatencyRiskCost;
        safetyReserveCost = newSafetyReserveCost;
        netEdge = newNetEdge;
        netEdgeRate = newNetEdgeRate;
        edgeRateScale = newEdgeRateScale;
        haircutRate = newHaircutRate;
        latencyRiskRate = newLatencyRiskRate;
        riskRateScale = newRiskRateScale;
        firstFeeGeneration = newFirstFeeGeneration;
        secondFeeGeneration = newSecondFeeGeneration;
        firstFundingGeneration = newFirstFundingGeneration;
        secondFundingGeneration = newSecondFundingGeneration;
        settlementGeneration = newSettlementGeneration;
        firstConversionGeneration = newFirstConversionGeneration;
        secondConversionGeneration = newSecondConversionGeneration;
        conversionCostGeneration = newConversionCostGeneration;
        slippageGeneration = newSlippageGeneration;
        safetyReserveGeneration = newSafetyReserveGeneration;
        liquidityRiskGeneration = newLiquidityRiskGeneration;
    }

    public PricingStatus status() {
        return status;
    }

    public BasisDirection direction() {
        return direction;
    }

    public int strategyId() {
        return strategyId;
    }

    public long configurationGeneration() {
        return configurationGeneration;
    }

    public long configurationHashHigh() {
        return configurationHashHigh;
    }

    public long configurationHashLow() {
        return configurationHashLow;
    }

    public long firstBookEpoch() {
        return firstBookEpoch;
    }

    public long firstBookSequence() {
        return firstBookSequence;
    }

    public long secondBookEpoch() {
        return secondBookEpoch;
    }

    public long secondBookSequence() {
        return secondBookSequence;
    }

    public int firstFeedProfileId() {
        return firstFeedProfileId;
    }

    public int secondFeedProfileId() {
        return secondFeedProfileId;
    }

    public long firstReceiveMonoNanos() {
        return firstReceiveMonoNanos;
    }

    public long secondReceiveMonoNanos() {
        return secondReceiveMonoNanos;
    }

    public long firstAgeNanos() {
        return firstAgeNanos;
    }

    public long secondAgeNanos() {
        return secondAgeNanos;
    }

    public long receiveSkewNanos() {
        return receiveSkewNanos;
    }

    public long firstMaximumAgeNanos() {
        return firstMaximumAgeNanos;
    }

    public long secondMaximumAgeNanos() {
        return secondMaximumAgeNanos;
    }

    public long maximumReceiveSkewNanos() {
        return maximumReceiveSkewNanos;
    }

    public long decisionMonoNanos() {
        return decisionMonoNanos;
    }

    public long decisionEpochNanos() {
        return decisionEpochNanos;
    }

    public long expiryMonoNanos() {
        return expiryMonoNanos;
    }

    public long maximumCanonicalExposure() {
        return maximumCanonicalExposure;
    }

    public long canonicalExposure() {
        return canonicalExposure;
    }

    public long firstNativeQuantity() {
        return firstNativeQuantity;
    }

    public long secondNativeQuantity() {
        return secondNativeQuantity;
    }

    public long firstAveragePrice() {
        return firstAveragePrice;
    }

    public long secondAveragePrice() {
        return secondAveragePrice;
    }

    public long firstWorstPrice() {
        return firstWorstPrice;
    }

    public long secondWorstPrice() {
        return secondWorstPrice;
    }

    public long grossProceeds() {
        return grossProceeds;
    }

    public long grossCost() {
        return grossCost;
    }

    public long firstFee() {
        return firstFee;
    }

    public long secondFee() {
        return secondFee;
    }

    public long fundingCost() {
        return fundingCost;
    }

    public long settlementCost() {
        return settlementCost;
    }

    public long conversionCost() {
        return conversionCost;
    }

    public long slippageCost() {
        return slippageCost;
    }

    public long latencyRiskCost() {
        return latencyRiskCost;
    }

    public long safetyReserveCost() {
        return safetyReserveCost;
    }

    public long netEdge() {
        return netEdge;
    }

    public long netEdgeRate() {
        return netEdgeRate;
    }

    public int edgeRateScale() {
        return edgeRateScale;
    }

    public long haircutRate() {
        return haircutRate;
    }

    public long latencyRiskRate() {
        return latencyRiskRate;
    }

    public int riskRateScale() {
        return riskRateScale;
    }

    public long firstFeeGeneration() {
        return firstFeeGeneration;
    }

    public long secondFeeGeneration() {
        return secondFeeGeneration;
    }

    public long firstFundingGeneration() {
        return firstFundingGeneration;
    }

    public long secondFundingGeneration() {
        return secondFundingGeneration;
    }

    public long settlementGeneration() {
        return settlementGeneration;
    }

    public long firstConversionGeneration() {
        return firstConversionGeneration;
    }

    public long secondConversionGeneration() {
        return secondConversionGeneration;
    }

    public long conversionCostGeneration() {
        return conversionCostGeneration;
    }

    public long slippageGeneration() {
        return slippageGeneration;
    }

    public long safetyReserveGeneration() {
        return safetyReserveGeneration;
    }

    public long liquidityRiskGeneration() {
        return liquidityRiskGeneration;
    }
}
