package com.penguinsecure.basis.strategy.api.definition;

/** Versioned source IDs for mandatory non-book economic inputs. */
public record EconomicSourceIds(
        int feeSourceId,
        int fundingSourceId,
        int conversionSourceId,
        int liquidityHaircutModelId,
        int slippageModelId,
        int latencyRiskModelId,
        int safetyReserveModelId) {}
