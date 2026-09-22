package com.penguinsecure.basis.strategy.api.definition;

/** Explicit registry IDs for every compiled model used by a definition. */
public record StrategyModelIds(
        int firstPayoffModelId,
        int secondPayoffModelId,
        int hedgeRatioModelId,
        int carryModelId,
        int signalModelId,
        int executionPolicyId) {}
