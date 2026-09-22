package com.penguinsecure.basis.strategy.basis.pricing;

import com.penguinsecure.basis.core.book.BookSide;
import com.penguinsecure.basis.core.book.ExecutablePriceStatus;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.book.MutableExecutablePrice;
import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.core.product.PayoffMath;
import com.penguinsecure.basis.strategy.api.definition.BasisStrategyDefinition;
import com.penguinsecure.basis.strategy.api.definition.BasisStrategyDefinitionValidator;
import com.penguinsecure.basis.strategy.api.definition.EconomicSourceIds;
import com.penguinsecure.basis.strategy.api.definition.StrategyValidationStatus;
import com.penguinsecure.basis.strategy.api.model.CarryModel;
import com.penguinsecure.basis.strategy.api.model.HedgeRatioModel;
import com.penguinsecure.basis.strategy.api.model.PayoffModel;
import com.penguinsecure.basis.strategy.api.model.SignalModel;
import com.penguinsecure.basis.strategy.api.model.StrategyModelRegistry;
import com.penguinsecure.basis.strategy.api.pricing.BasisDirection;
import com.penguinsecure.basis.strategy.api.pricing.EconomicInputs;
import com.penguinsecure.basis.strategy.api.pricing.EconomicRate;
import com.penguinsecure.basis.strategy.api.pricing.InputMetadata;
import com.penguinsecure.basis.strategy.api.pricing.LiquidityRiskTable;
import com.penguinsecure.basis.strategy.api.pricing.MutableBasisOpportunity;
import com.penguinsecure.basis.strategy.api.pricing.MutableTemporalEvidence;
import com.penguinsecure.basis.strategy.api.pricing.PricingStatus;
import com.penguinsecure.basis.strategy.api.pricing.TemporalCoherenceGate;

/** Allocation-free reusable basis evaluator for both configured leg directions. */
public final class CrossVenueBasisStrategy {
    public static final int EDGE_RATE_SCALE = 8;

    private final BasisStrategyDefinition definition;
    private final InstrumentDefinition firstInstrument;
    private final InstrumentDefinition secondInstrument;
    private final PayoffModel firstPayoff;
    private final PayoffModel secondPayoff;
    private final HedgeRatioModel hedgeRatio;
    private final CarryModel carryModel;
    private final SignalModel signalModel;
    private final int canonicalExposureScale;
    private final int riskCurrencyScale;
    private final MutableTemporalEvidence temporal = new MutableTemporalEvidence();
    private final MutableExecutablePrice firstPrice = new MutableExecutablePrice();
    private final MutableExecutablePrice secondPrice = new MutableExecutablePrice();
    private final MutableLongResult scratch = new MutableLongResult();
    private final MutableLongResult value = new MutableLongResult();

    public CrossVenueBasisStrategy(
            final BasisStrategyDefinition definition,
            final InstrumentDefinition firstInstrument,
            final InstrumentDefinition secondInstrument,
            final StrategyModelRegistry registry,
            final int canonicalExposureScale,
            final int riskCurrencyScale) {
        if (BasisStrategyDefinitionValidator.validate(definition, registry)
                != StrategyValidationStatus.VALID) {
            throw new IllegalArgumentException("definition models are not fully registered");
        }
        if (firstInstrument == null || secondInstrument == null) {
            throw new NullPointerException("instrument definitions are required");
        }
        if (canonicalExposureScale < 0
                || canonicalExposureScale > 18
                || riskCurrencyScale < 0
                || riskCurrencyScale > 18) {
            throw new IllegalArgumentException("invalid output scale");
        }
        if (!matches(definition.firstLeg(), firstInstrument)
                || !matches(definition.secondLeg(), secondInstrument)) {
            throw new IllegalArgumentException("instrument route does not match strategy leg");
        }
        this.definition = definition;
        this.firstInstrument = firstInstrument;
        this.secondInstrument = secondInstrument;
        firstPayoff = registry.payoff(definition.modelIds().firstPayoffModelId());
        secondPayoff = registry.payoff(definition.modelIds().secondPayoffModelId());
        hedgeRatio = registry.hedgeRatio(definition.modelIds().hedgeRatioModelId());
        carryModel = registry.carry(definition.modelIds().carryModelId());
        signalModel = registry.signal(definition.modelIds().signalModelId());
        if (!firstPayoff.supports(firstInstrument) || !secondPayoff.supports(secondInstrument)) {
            throw new IllegalArgumentException("payoff model does not support instrument");
        }
        this.canonicalExposureScale = canonicalExposureScale;
        this.riskCurrencyScale = riskCurrencyScale;
    }

    public PricingStatus evaluate(
            final BasisDirection direction,
            final FixedDepthOrderBook firstBook,
            final FixedDepthOrderBook secondBook,
            final EconomicInputs inputs,
            final long requestedCanonicalExposure,
            final int volatilityRegime,
            final long configurationGeneration,
            final long decisionMonoNanos,
            final long decisionEpochNanos,
            final MutableBasisOpportunity result) {
        if (direction == null
                || firstBook == null
                || secondBook == null
                || inputs == null
                || result == null
                || requestedCanonicalExposure <= 0
                || configurationGeneration <= 0
                || decisionEpochNanos <= 0) {
            if (result != null) result.reject(PricingStatus.INVALID_ARGUMENT, direction);
            return PricingStatus.INVALID_ARGUMENT;
        }
        if (!matches(definition.firstLeg(), firstBook)
                || !matches(definition.secondLeg(), secondBook)) {
            return reject(result, PricingStatus.BOOK_ROUTE_MISMATCH, direction);
        }
        final PricingStatus temporalStatus =
                TemporalCoherenceGate.evaluate(
                        firstBook,
                        secondBook,
                        definition.firstLeg().maximumAgeNanos(),
                        definition.secondLeg().maximumAgeNanos(),
                        definition.maximumReceiveSkewNanos(),
                        decisionMonoNanos,
                        temporal);
        if (temporalStatus != PricingStatus.OPPORTUNITY) {
            return reject(result, temporalStatus, direction);
        }
        if (!validInputs(inputs, decisionEpochNanos, decisionMonoNanos)) {
            return reject(result, PricingStatus.ECONOMIC_INPUT_INVALID, direction);
        }

        final LiquidityRiskTable table = inputs.liquidityRisk();
        final int row =
                table.find(
                        direction,
                        firstInstrument.venueId(),
                        secondInstrument.venueId(),
                        requestedCanonicalExposure,
                        volatilityRegime);
        if (row < 0) return reject(result, PricingStatus.ECONOMIC_INPUT_INVALID, direction);
        final long haircutRate = table.haircutRate(row);
        final long latencyRate = table.latencyRiskRate(row);

        final BookSide firstSide =
                direction == BasisDirection.BUY_FIRST_SELL_SECOND ? BookSide.ASK : BookSide.BID;
        final BookSide secondSide =
                direction == BasisDirection.BUY_FIRST_SELL_SECOND ? BookSide.BID : BookSide.ASK;
        final long firstMaximum =
                maximumExposure(
                        firstBook,
                        firstSide,
                        firstPayoff,
                        firstInstrument,
                        haircutRate,
                        table.rateScale());
        if (firstMaximum <= 0) return reject(result, PricingStatus.NO_EXECUTABLE_DEPTH, direction);
        final long secondMaximum =
                maximumExposure(
                        secondBook,
                        secondSide,
                        secondPayoff,
                        secondInstrument,
                        haircutRate,
                        table.rateScale());
        if (secondMaximum <= 0) return reject(result, PricingStatus.NO_EXECUTABLE_DEPTH, direction);
        long maximumExposure = Math.min(firstMaximum, secondMaximum);
        maximumExposure = Math.min(maximumExposure, definition.riskLimits().maximumGrossExposure());
        final long targetExposure = Math.min(requestedCanonicalExposure, maximumExposure);
        if (targetExposure <= 0)
            return reject(result, PricingStatus.NO_EXECUTABLE_DEPTH, direction);

        if (nativeQuantity(
                        hedgeRatio,
                        firstPayoff,
                        firstInstrument,
                        targetExposure,
                        firstBook.bestPriceTicks(firstSide),
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        long firstQuantity = value.value();
        if (nativeQuantity(
                        hedgeRatio,
                        secondPayoff,
                        secondInstrument,
                        targetExposure,
                        secondBook.bestPriceTicks(secondSide),
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        long secondQuantity = value.value();

        PricingStatus executionStatus =
                execute(firstBook, firstSide, firstQuantity, decisionMonoNanos, firstPrice);
        if (executionStatus != PricingStatus.OPPORTUNITY) {
            return reject(result, executionStatus, direction);
        }
        executionStatus =
                execute(secondBook, secondSide, secondQuantity, decisionMonoNanos, secondPrice);
        if (executionStatus != PricingStatus.OPPORTUNITY) {
            return reject(result, executionStatus, direction);
        }

        // Inverse quantities depend on price. Re-match once using executable VWAP, then re-walk
        // depth.
        if (nativeQuantity(
                        hedgeRatio,
                        firstPayoff,
                        firstInstrument,
                        targetExposure,
                        firstPrice.averagePriceTicks(),
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        firstQuantity = value.value();
        if (nativeQuantity(
                        hedgeRatio,
                        secondPayoff,
                        secondInstrument,
                        targetExposure,
                        secondPrice.averagePriceTicks(),
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        secondQuantity = value.value();
        if (execute(firstBook, firstSide, firstQuantity, decisionMonoNanos, firstPrice)
                        != PricingStatus.OPPORTUNITY
                || execute(secondBook, secondSide, secondQuantity, decisionMonoNanos, secondPrice)
                        != PricingStatus.OPPORTUNITY) {
            return reject(result, PricingStatus.PARTIAL_EXECUTION, direction);
        }

        if (firstPayoff.canonicalExposure(
                        firstInstrument,
                        firstQuantity,
                        firstPrice.averagePriceTicks(),
                        canonicalExposureScale,
                        RoundingPolicy.FLOOR,
                        scratch,
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long firstExposure = value.value();
        if (secondPayoff.canonicalExposure(
                        secondInstrument,
                        secondQuantity,
                        secondPrice.averagePriceTicks(),
                        canonicalExposureScale,
                        RoundingPolicy.FLOOR,
                        scratch,
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long secondExposure = value.value();
        final long imbalance =
                firstExposure >= secondExposure
                        ? firstExposure - secondExposure
                        : secondExposure - firstExposure;
        if (imbalance > definition.riskLimits().maximumImbalance()) {
            return reject(result, PricingStatus.EXPOSURE_IMBALANCE, direction);
        }
        final long actualExposure = Math.min(firstExposure, secondExposure);

        if (convertedRiskValue(
                        firstPayoff,
                        firstInstrument,
                        firstQuantity,
                        firstPrice.averagePriceTicks(),
                        inputs.firstConversion(),
                        firstSide == BookSide.ASK ? RoundingPolicy.CEILING : RoundingPolicy.FLOOR,
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long firstRiskValue = value.value();
        if (convertedRiskValue(
                        secondPayoff,
                        secondInstrument,
                        secondQuantity,
                        secondPrice.averagePriceTicks(),
                        inputs.secondConversion(),
                        secondSide == BookSide.ASK ? RoundingPolicy.CEILING : RoundingPolicy.FLOOR,
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long secondRiskValue = value.value();
        final boolean firstBuy = firstSide == BookSide.ASK;
        final long grossCost = firstBuy ? firstRiskValue : secondRiskValue;
        final long grossProceeds = firstBuy ? secondRiskValue : firstRiskValue;

        if (rateCost(
                        firstRiskValue,
                        inputs.firstFees().takerRate(),
                        inputs.firstFees().scale(),
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long firstFee = value.value();
        if (rateCost(
                        secondRiskValue,
                        inputs.secondFees().takerRate(),
                        inputs.secondFees().scale(),
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long secondFee = value.value();
        if (carryModel.fundingCost(
                        firstRiskValue,
                        inputs.firstFunding(),
                        firstBuy,
                        secondRiskValue,
                        inputs.secondFunding(),
                        !firstBuy,
                        riskCurrencyScale,
                        scratch,
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long fundingCost = value.value();
        if (carryModel.settlementCost(
                        firstRiskValue,
                        secondRiskValue,
                        inputs.settlementCarry(),
                        firstBuy,
                        riskCurrencyScale,
                        scratch,
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long settlementCost = value.value();
        final long referenceValue = Math.max(grossCost, grossProceeds);
        if (economicRateCost(referenceValue, inputs.conversionCost(), value) != NumericStatus.OK) {
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        }
        final long conversionCost = value.value();
        if (economicRateCost(referenceValue, inputs.slippage(), value) != NumericStatus.OK) {
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        }
        final long slippageCost = value.value();
        if (rateCost(referenceValue, latencyRate, table.rateScale(), value) != NumericStatus.OK) {
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        }
        final long latencyCost = value.value();
        if (economicRateCost(referenceValue, inputs.safetyReserve(), value) != NumericStatus.OK) {
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        }
        final long reserveCost = value.value();

        if (CheckedDecimalMath.add(firstFee, secondFee, value) != NumericStatus.OK
                || CheckedDecimalMath.add(value.value(), fundingCost, value) != NumericStatus.OK
                || CheckedDecimalMath.add(value.value(), settlementCost, value) != NumericStatus.OK
                || CheckedDecimalMath.add(value.value(), conversionCost, value) != NumericStatus.OK
                || CheckedDecimalMath.add(value.value(), slippageCost, value) != NumericStatus.OK
                || CheckedDecimalMath.add(value.value(), latencyCost, value) != NumericStatus.OK
                || CheckedDecimalMath.add(value.value(), reserveCost, value) != NumericStatus.OK) {
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        }
        final long totalCosts = value.value();
        if (CheckedDecimalMath.subtract(grossProceeds, grossCost, value) != NumericStatus.OK
                || CheckedDecimalMath.subtract(value.value(), totalCosts, value)
                        != NumericStatus.OK) {
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        }
        final long netEdge = value.value();
        if (CheckedDecimalMath.multiplyDivide(
                        netEdge,
                        CheckedDecimalMath.powerOfTen(EDGE_RATE_SCALE),
                        referenceValue,
                        RoundingPolicy.FLOOR,
                        scratch,
                        value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);
        final long netEdgeRate = value.value();
        final PricingStatus finalStatus =
                signalModel.isEntry(netEdgeRate, definition.entryThreshold())
                        ? PricingStatus.OPPORTUNITY
                        : PricingStatus.BELOW_THRESHOLD;
        if (CheckedDecimalMath.add(decisionMonoNanos, definition.opportunityExpiryNanos(), value)
                != NumericStatus.OK)
            return reject(result, PricingStatus.NUMERIC_FAILURE, direction);

        result.set(
                finalStatus,
                direction,
                definition.strategyId(),
                configurationGeneration,
                definition.configurationHashHigh(),
                definition.configurationHashLow(),
                firstBook.epoch(),
                firstBook.lastSequence(),
                secondBook.epoch(),
                secondBook.lastSequence(),
                firstBook.feedProfileId(),
                secondBook.feedProfileId(),
                firstBook.lastReceiveMonoNanos(),
                secondBook.lastReceiveMonoNanos(),
                temporal.firstAgeNanos(),
                temporal.secondAgeNanos(),
                temporal.receiveSkewNanos(),
                definition.firstLeg().maximumAgeNanos(),
                definition.secondLeg().maximumAgeNanos(),
                definition.maximumReceiveSkewNanos(),
                decisionMonoNanos,
                decisionEpochNanos,
                value.value(),
                maximumExposure,
                actualExposure,
                firstQuantity,
                secondQuantity,
                firstPrice.averagePriceTicks(),
                secondPrice.averagePriceTicks(),
                firstPrice.worstPriceTicks(),
                secondPrice.worstPriceTicks(),
                grossProceeds,
                grossCost,
                firstFee,
                secondFee,
                fundingCost,
                settlementCost,
                conversionCost,
                slippageCost,
                latencyCost,
                reserveCost,
                netEdge,
                netEdgeRate,
                EDGE_RATE_SCALE,
                haircutRate,
                latencyRate,
                table.rateScale(),
                inputs.firstFees().metadata().generation(),
                inputs.secondFees().metadata().generation(),
                inputs.firstFunding().metadata().generation(),
                inputs.secondFunding().metadata().generation(),
                inputs.settlementCarry().metadata().generation(),
                inputs.firstConversion().metadata().generation(),
                inputs.secondConversion().metadata().generation(),
                inputs.conversionCost().metadata().generation(),
                inputs.slippage().metadata().generation(),
                inputs.safetyReserve().metadata().generation(),
                table.metadata().generation());
        return finalStatus;
    }

    private long maximumExposure(
            final FixedDepthOrderBook book,
            final BookSide side,
            final PayoffModel payoff,
            final InstrumentDefinition instrument,
            final long haircutRate,
            final int rateScale) {
        long total = 0;
        final int depth = book.depth(side);
        if (depth == 0) return 0;
        for (int index = 0; index < depth; index++) {
            if (CheckedDecimalMath.add(total, book.quantityLots(side, index), value)
                    != NumericStatus.OK) return 0;
            total = value.value();
        }
        final long factor = CheckedDecimalMath.powerOfTen(rateScale);
        if (CheckedDecimalMath.multiplyDivide(
                        total, factor - haircutRate, factor, RoundingPolicy.FLOOR, scratch, value)
                != NumericStatus.OK) return 0;
        final long conservativePrice =
                side == BookSide.ASK ? book.priceTicks(side, depth - 1) : book.priceTicks(side, 0);
        if (payoff.canonicalExposure(
                        instrument,
                        value.value(),
                        conservativePrice,
                        canonicalExposureScale,
                        RoundingPolicy.FLOOR,
                        scratch,
                        value)
                != NumericStatus.OK) return 0;
        return value.value();
    }

    private NumericStatus nativeQuantity(
            final HedgeRatioModel model,
            final PayoffModel payoff,
            final InstrumentDefinition instrument,
            final long exposure,
            final long price,
            final MutableLongResult result) {
        return model.nativeQuantity(
                payoff,
                instrument,
                exposure,
                canonicalExposureScale,
                price,
                definition.hedgeRounding(),
                scratch,
                result);
    }

    private static PricingStatus execute(
            final FixedDepthOrderBook book,
            final BookSide side,
            final long quantity,
            final long decisionMonoNanos,
            final MutableExecutablePrice result) {
        final long limit = side == BookSide.ASK ? Long.MAX_VALUE : 1;
        final ExecutablePriceStatus status =
                book.executablePrice(side, quantity, limit, decisionMonoNanos, result);
        return switch (status) {
            case OK -> PricingStatus.OPPORTUNITY;
            case PARTIAL -> PricingStatus.PARTIAL_EXECUTION;
            case UNTRUSTED -> PricingStatus.BOOK_UNTRUSTED;
            case INVALID_ARGUMENT -> PricingStatus.INVALID_ARGUMENT;
            case OVERFLOW -> PricingStatus.NUMERIC_FAILURE;
        };
    }

    private NumericStatus convertedRiskValue(
            final PayoffModel model,
            final InstrumentDefinition instrument,
            final long quantity,
            final long price,
            final com.penguinsecure.basis.strategy.api.pricing.ConversionRate conversion,
            final RoundingPolicy rounding,
            final MutableLongResult result) {
        if (model.riskValue(
                        instrument, quantity, price, riskCurrencyScale, rounding, scratch, result)
                != NumericStatus.OK) return result.status();
        final long unconverted = result.value();
        return PayoffMath.convertCurrency(
                unconverted,
                riskCurrencyScale,
                conversion.value(),
                conversion.scale(),
                riskCurrencyScale,
                rounding,
                scratch,
                result);
    }

    private NumericStatus economicRateCost(
            final long amount, final EconomicRate rate, final MutableLongResult result) {
        return rateCost(amount, rate.value(), rate.scale(), result);
    }

    private NumericStatus rateCost(
            final long amount,
            final long rate,
            final int rateScale,
            final MutableLongResult result) {
        return PayoffMath.rateAmount(
                amount,
                riskCurrencyScale,
                rate,
                rateScale,
                riskCurrencyScale,
                RoundingPolicy.CEILING,
                scratch,
                result);
    }

    private boolean validInputs(
            final EconomicInputs inputs,
            final long decisionEpochNanos,
            final long decisionMonoNanos) {
        final EconomicSourceIds sources = definition.economicSourceIds();
        return usable(
                        inputs.firstFees().metadata(),
                        sources.feeSourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.secondFees().metadata(),
                        sources.feeSourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.firstFunding().metadata(),
                        sources.fundingSourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.secondFunding().metadata(),
                        sources.fundingSourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.settlementCarry().metadata(),
                        sources.fundingSourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.firstConversion().metadata(),
                        sources.conversionSourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.secondConversion().metadata(),
                        sources.conversionSourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.conversionCost().metadata(),
                        sources.conversionSourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.slippage().metadata(),
                        sources.slippageModelId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && usable(
                        inputs.safetyReserve().metadata(),
                        sources.safetyReserveModelId(),
                        decisionEpochNanos,
                        decisionMonoNanos)
                && inputs.firstFees().takerRate() >= 0
                && inputs.secondFees().takerRate() >= 0
                && inputs.conversionCost().value() >= 0
                && inputs.slippage().value() >= 0
                && inputs.safetyReserve().value() >= 0
                && inputs.liquidityRisk().haircutModelId() == sources.liquidityHaircutModelId()
                && inputs.liquidityRisk().latencyRiskModelId() == sources.latencyRiskModelId()
                && usable(
                        inputs.liquidityRisk().metadata(),
                        inputs.liquidityRisk().metadata().sourceId(),
                        decisionEpochNanos,
                        decisionMonoNanos);
    }

    private static boolean usable(
            final InputMetadata metadata,
            final int expectedSource,
            final long decisionEpochNanos,
            final long decisionMonoNanos) {
        return metadata.isUsable(expectedSource, decisionEpochNanos, decisionMonoNanos);
    }

    private static boolean matches(
            final com.penguinsecure.basis.strategy.api.definition.StrategyLegDefinition leg,
            final InstrumentDefinition instrument) {
        return leg.venueId() == instrument.venueId()
                && leg.instrumentId() == instrument.instrumentId();
    }

    private static boolean matches(
            final com.penguinsecure.basis.strategy.api.definition.StrategyLegDefinition leg,
            final FixedDepthOrderBook book) {
        return leg.venueId() == book.venueId()
                && leg.instrumentId() == book.instrumentId()
                && leg.feedProfileId() == book.feedProfileId();
    }

    private static PricingStatus reject(
            final MutableBasisOpportunity result,
            final PricingStatus status,
            final BasisDirection direction) {
        result.reject(status, direction);
        return status;
    }
}
