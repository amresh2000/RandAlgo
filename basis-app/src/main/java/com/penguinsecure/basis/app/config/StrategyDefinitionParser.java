package com.penguinsecure.basis.app.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.strategy.api.definition.BasisStrategyDefinition;
import com.penguinsecure.basis.strategy.api.definition.BasisStrategyDefinitionValidator;
import com.penguinsecure.basis.strategy.api.definition.EconomicSourceIds;
import com.penguinsecure.basis.strategy.api.definition.StrategyLegDefinition;
import com.penguinsecure.basis.strategy.api.definition.StrategyLifecycle;
import com.penguinsecure.basis.strategy.api.definition.StrategyModelIds;
import com.penguinsecure.basis.strategy.api.definition.StrategyRiskLimits;
import com.penguinsecure.basis.strategy.api.definition.StrategyValidationStatus;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/** Strict streaming parser for the versioned flat JSON onboarding contract. */
public final class StrategyDefinitionParser {
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final Set<String> REQUIRED_FIELDS =
            Set.of(
                    "strategyId",
                    "versionMajor",
                    "versionMinor",
                    "versionPatch",
                    "lifecycle",
                    "effectiveEpochNanos",
                    "firstVenueId",
                    "firstInstrumentId",
                    "firstAccountId",
                    "firstFeedProfileId",
                    "firstMaximumAgeNanos",
                    "secondVenueId",
                    "secondInstrumentId",
                    "secondAccountId",
                    "secondFeedProfileId",
                    "secondMaximumAgeNanos",
                    "underlyingCurrencyId",
                    "riskCurrencyId",
                    "firstPayoffModelId",
                    "secondPayoffModelId",
                    "hedgeRatioModelId",
                    "carryModelId",
                    "signalModelId",
                    "executionPolicyId",
                    "feeSourceId",
                    "fundingSourceId",
                    "conversionSourceId",
                    "liquidityHaircutModelId",
                    "slippageModelId",
                    "latencyRiskModelId",
                    "safetyReserveModelId",
                    "hedgeRounding",
                    "entryThreshold",
                    "exitThreshold",
                    "holdingHorizonNanos",
                    "opportunityExpiryNanos",
                    "maximumReceiveSkewNanos",
                    "marketDataToWriteP99Nanos",
                    "marketDataToWriteP999Nanos",
                    "fillToHedgeWriteP99Nanos",
                    "fillToHedgeWriteP999Nanos",
                    "maximumGrossExposure",
                    "maximumNetExposure",
                    "maximumUnhedgedExposure",
                    "maximumImbalance",
                    "capitalLimit",
                    "collateralLimit",
                    "maximumConcurrentGroups");

    public BasisStrategyDefinition parse(final byte[] json)
            throws StrategyDefinitionParseException {
        if (json == null) {
            throw new StrategyDefinitionParseException("definition bytes are required");
        }
        final Builder values = new Builder();
        final Set<String> seen = new HashSet<>(REQUIRED_FIELDS.size());
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new StrategyDefinitionParseException("definition root must be an object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw new StrategyDefinitionParseException("expected a field name");
                }
                final String field = parser.currentName();
                if (!REQUIRED_FIELDS.contains(field)) {
                    throw new StrategyDefinitionParseException("unknown field: " + field);
                }
                if (!seen.add(field)) {
                    throw new StrategyDefinitionParseException("duplicate field: " + field);
                }
                if (parser.nextToken() == null) {
                    throw new StrategyDefinitionParseException("missing value for: " + field);
                }
                readField(values, field, parser);
            }
            if (parser.nextToken() != null) {
                throw new StrategyDefinitionParseException("trailing content is forbidden");
            }
        } catch (StrategyDefinitionParseException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new StrategyDefinitionParseException("invalid strategy definition", exception);
        }

        if (seen.size() != REQUIRED_FIELDS.size()) {
            final Set<String> missing = new HashSet<>(REQUIRED_FIELDS);
            missing.removeAll(seen);
            throw new StrategyDefinitionParseException("missing fields: " + missing);
        }
        final long[] hash = canonicalHash(values);
        final BasisStrategyDefinition definition = values.build(hash[0], hash[1]);
        final StrategyValidationStatus status =
                BasisStrategyDefinitionValidator.validate(definition);
        if (status != StrategyValidationStatus.VALID) {
            throw new StrategyDefinitionParseException("definition rejected: " + status);
        }
        return definition;
    }

    private static void readField(final Builder value, final String field, final JsonParser parser)
            throws IOException, StrategyDefinitionParseException {
        switch (field) {
            case "strategyId" -> value.strategyId = integer(parser, field);
            case "versionMajor" -> value.versionMajor = integer(parser, field);
            case "versionMinor" -> value.versionMinor = integer(parser, field);
            case "versionPatch" -> value.versionPatch = integer(parser, field);
            case "lifecycle" -> value.lifecycle = StrategyLifecycle.valueOf(text(parser, field));
            case "effectiveEpochNanos" -> value.effectiveEpochNanos = longValue(parser, field);
            case "firstVenueId" -> value.firstVenueId = integer(parser, field);
            case "firstInstrumentId" -> value.firstInstrumentId = integer(parser, field);
            case "firstAccountId" -> value.firstAccountId = integer(parser, field);
            case "firstFeedProfileId" -> value.firstFeedProfileId = integer(parser, field);
            case "firstMaximumAgeNanos" -> value.firstMaximumAgeNanos = longValue(parser, field);
            case "secondVenueId" -> value.secondVenueId = integer(parser, field);
            case "secondInstrumentId" -> value.secondInstrumentId = integer(parser, field);
            case "secondAccountId" -> value.secondAccountId = integer(parser, field);
            case "secondFeedProfileId" -> value.secondFeedProfileId = integer(parser, field);
            case "secondMaximumAgeNanos" -> value.secondMaximumAgeNanos = longValue(parser, field);
            case "underlyingCurrencyId" -> value.underlyingCurrencyId = integer(parser, field);
            case "riskCurrencyId" -> value.riskCurrencyId = integer(parser, field);
            case "firstPayoffModelId" -> value.firstPayoffModelId = integer(parser, field);
            case "secondPayoffModelId" -> value.secondPayoffModelId = integer(parser, field);
            case "hedgeRatioModelId" -> value.hedgeRatioModelId = integer(parser, field);
            case "carryModelId" -> value.carryModelId = integer(parser, field);
            case "signalModelId" -> value.signalModelId = integer(parser, field);
            case "executionPolicyId" -> value.executionPolicyId = integer(parser, field);
            case "feeSourceId" -> value.feeSourceId = integer(parser, field);
            case "fundingSourceId" -> value.fundingSourceId = integer(parser, field);
            case "conversionSourceId" -> value.conversionSourceId = integer(parser, field);
            case "liquidityHaircutModelId" ->
                    value.liquidityHaircutModelId = integer(parser, field);
            case "slippageModelId" -> value.slippageModelId = integer(parser, field);
            case "latencyRiskModelId" -> value.latencyRiskModelId = integer(parser, field);
            case "safetyReserveModelId" -> value.safetyReserveModelId = integer(parser, field);
            case "hedgeRounding" ->
                    value.hedgeRounding = RoundingPolicy.valueOf(text(parser, field));
            case "entryThreshold" -> value.entryThreshold = longValue(parser, field);
            case "exitThreshold" -> value.exitThreshold = longValue(parser, field);
            case "holdingHorizonNanos" -> value.holdingHorizonNanos = longValue(parser, field);
            case "opportunityExpiryNanos" ->
                    value.opportunityExpiryNanos = longValue(parser, field);
            case "maximumReceiveSkewNanos" ->
                    value.maximumReceiveSkewNanos = longValue(parser, field);
            case "marketDataToWriteP99Nanos" ->
                    value.marketDataToWriteP99Nanos = longValue(parser, field);
            case "marketDataToWriteP999Nanos" ->
                    value.marketDataToWriteP999Nanos = longValue(parser, field);
            case "fillToHedgeWriteP99Nanos" ->
                    value.fillToHedgeWriteP99Nanos = longValue(parser, field);
            case "fillToHedgeWriteP999Nanos" ->
                    value.fillToHedgeWriteP999Nanos = longValue(parser, field);
            case "maximumGrossExposure" -> value.maximumGrossExposure = longValue(parser, field);
            case "maximumNetExposure" -> value.maximumNetExposure = longValue(parser, field);
            case "maximumUnhedgedExposure" ->
                    value.maximumUnhedgedExposure = longValue(parser, field);
            case "maximumImbalance" -> value.maximumImbalance = longValue(parser, field);
            case "capitalLimit" -> value.capitalLimit = longValue(parser, field);
            case "collateralLimit" -> value.collateralLimit = longValue(parser, field);
            case "maximumConcurrentGroups" ->
                    value.maximumConcurrentGroups = integer(parser, field);
            default -> throw new StrategyDefinitionParseException("unknown field: " + field);
        }
    }

    private static int integer(final JsonParser parser, final String field)
            throws IOException, StrategyDefinitionParseException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw new StrategyDefinitionParseException(field + " must be an integer");
        }
        return parser.getIntValue();
    }

    private static long longValue(final JsonParser parser, final String field)
            throws IOException, StrategyDefinitionParseException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw new StrategyDefinitionParseException(field + " must be an integer");
        }
        return parser.getLongValue();
    }

    private static String text(final JsonParser parser, final String field)
            throws IOException, StrategyDefinitionParseException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new StrategyDefinitionParseException(field + " must be a string");
        }
        return parser.getText();
    }

    private static long[] canonicalHash(final Builder value)
            throws StrategyDefinitionParseException {
        final byte[] canonical = value.canonicalForm().getBytes(StandardCharsets.UTF_8);
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            final ByteBuffer bytes = ByteBuffer.wrap(digest);
            return new long[] {bytes.getLong(), bytes.getLong()};
        } catch (NoSuchAlgorithmException exception) {
            throw new StrategyDefinitionParseException("SHA-256 unavailable", exception);
        }
    }

    @SuppressWarnings("checkstyle:MemberName")
    private static final class Builder {
        private int strategyId;
        private int versionMajor;
        private int versionMinor;
        private int versionPatch;
        private StrategyLifecycle lifecycle;
        private long effectiveEpochNanos;
        private int firstVenueId;
        private int firstInstrumentId;
        private int firstAccountId;
        private int firstFeedProfileId;
        private long firstMaximumAgeNanos;
        private int secondVenueId;
        private int secondInstrumentId;
        private int secondAccountId;
        private int secondFeedProfileId;
        private long secondMaximumAgeNanos;
        private int underlyingCurrencyId;
        private int riskCurrencyId;
        private int firstPayoffModelId;
        private int secondPayoffModelId;
        private int hedgeRatioModelId;
        private int carryModelId;
        private int signalModelId;
        private int executionPolicyId;
        private int feeSourceId;
        private int fundingSourceId;
        private int conversionSourceId;
        private int liquidityHaircutModelId;
        private int slippageModelId;
        private int latencyRiskModelId;
        private int safetyReserveModelId;
        private RoundingPolicy hedgeRounding;
        private long entryThreshold;
        private long exitThreshold;
        private long holdingHorizonNanos;
        private long opportunityExpiryNanos;
        private long maximumReceiveSkewNanos;
        private long marketDataToWriteP99Nanos;
        private long marketDataToWriteP999Nanos;
        private long fillToHedgeWriteP99Nanos;
        private long fillToHedgeWriteP999Nanos;
        private long maximumGrossExposure;
        private long maximumNetExposure;
        private long maximumUnhedgedExposure;
        private long maximumImbalance;
        private long capitalLimit;
        private long collateralLimit;
        private int maximumConcurrentGroups;

        private BasisStrategyDefinition build(final long hashHigh, final long hashLow) {
            return new BasisStrategyDefinition(
                    strategyId,
                    versionMajor,
                    versionMinor,
                    versionPatch,
                    lifecycle,
                    effectiveEpochNanos,
                    hashHigh,
                    hashLow,
                    new StrategyLegDefinition(
                            firstVenueId,
                            firstInstrumentId,
                            firstAccountId,
                            firstFeedProfileId,
                            firstMaximumAgeNanos),
                    new StrategyLegDefinition(
                            secondVenueId,
                            secondInstrumentId,
                            secondAccountId,
                            secondFeedProfileId,
                            secondMaximumAgeNanos),
                    underlyingCurrencyId,
                    riskCurrencyId,
                    new StrategyModelIds(
                            firstPayoffModelId,
                            secondPayoffModelId,
                            hedgeRatioModelId,
                            carryModelId,
                            signalModelId,
                            executionPolicyId),
                    new EconomicSourceIds(
                            feeSourceId,
                            fundingSourceId,
                            conversionSourceId,
                            liquidityHaircutModelId,
                            slippageModelId,
                            latencyRiskModelId,
                            safetyReserveModelId),
                    hedgeRounding,
                    entryThreshold,
                    exitThreshold,
                    holdingHorizonNanos,
                    opportunityExpiryNanos,
                    maximumReceiveSkewNanos,
                    marketDataToWriteP99Nanos,
                    marketDataToWriteP999Nanos,
                    fillToHedgeWriteP99Nanos,
                    fillToHedgeWriteP999Nanos,
                    new StrategyRiskLimits(
                            maximumGrossExposure,
                            maximumNetExposure,
                            maximumUnhedgedExposure,
                            maximumImbalance,
                            capitalLimit,
                            collateralLimit,
                            maximumConcurrentGroups));
        }

        private String canonicalForm() {
            return strategyId
                    + "|"
                    + versionMajor
                    + "|"
                    + versionMinor
                    + "|"
                    + versionPatch
                    + "|"
                    + lifecycle
                    + "|"
                    + effectiveEpochNanos
                    + "|"
                    + firstVenueId
                    + "|"
                    + firstInstrumentId
                    + "|"
                    + firstAccountId
                    + "|"
                    + firstFeedProfileId
                    + "|"
                    + firstMaximumAgeNanos
                    + "|"
                    + secondVenueId
                    + "|"
                    + secondInstrumentId
                    + "|"
                    + secondAccountId
                    + "|"
                    + secondFeedProfileId
                    + "|"
                    + secondMaximumAgeNanos
                    + "|"
                    + underlyingCurrencyId
                    + "|"
                    + riskCurrencyId
                    + "|"
                    + firstPayoffModelId
                    + "|"
                    + secondPayoffModelId
                    + "|"
                    + hedgeRatioModelId
                    + "|"
                    + carryModelId
                    + "|"
                    + signalModelId
                    + "|"
                    + executionPolicyId
                    + "|"
                    + feeSourceId
                    + "|"
                    + fundingSourceId
                    + "|"
                    + conversionSourceId
                    + "|"
                    + liquidityHaircutModelId
                    + "|"
                    + slippageModelId
                    + "|"
                    + latencyRiskModelId
                    + "|"
                    + safetyReserveModelId
                    + "|"
                    + hedgeRounding
                    + "|"
                    + entryThreshold
                    + "|"
                    + exitThreshold
                    + "|"
                    + holdingHorizonNanos
                    + "|"
                    + opportunityExpiryNanos
                    + "|"
                    + maximumReceiveSkewNanos
                    + "|"
                    + marketDataToWriteP99Nanos
                    + "|"
                    + marketDataToWriteP999Nanos
                    + "|"
                    + fillToHedgeWriteP99Nanos
                    + "|"
                    + fillToHedgeWriteP999Nanos
                    + "|"
                    + maximumGrossExposure
                    + "|"
                    + maximumNetExposure
                    + "|"
                    + maximumUnhedgedExposure
                    + "|"
                    + maximumImbalance
                    + "|"
                    + capitalLimit
                    + "|"
                    + collateralLimit
                    + "|"
                    + maximumConcurrentGroups;
        }
    }
}
