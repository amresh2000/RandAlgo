package com.penguinsecure.basis.strategy.api.model;

/** Fixed-capacity explicit model registry; registration is cold-path and reflection-free. */
public final class StrategyModelRegistry {
    private final PayoffModel[] payoffModels;
    private final HedgeRatioModel[] hedgeRatioModels;
    private final CarryModel[] carryModels;
    private final SignalModel[] signalModels;
    private final ExecutionPolicyModel[] executionPolicies;
    private boolean frozen;

    public StrategyModelRegistry(final int maximumModelId) {
        if (maximumModelId <= 0 || maximumModelId > 65_535) {
            throw new IllegalArgumentException("maximumModelId must be in [1, 65535]");
        }
        payoffModels = new PayoffModel[maximumModelId + 1];
        hedgeRatioModels = new HedgeRatioModel[maximumModelId + 1];
        carryModels = new CarryModel[maximumModelId + 1];
        signalModels = new SignalModel[maximumModelId + 1];
        executionPolicies = new ExecutionPolicyModel[maximumModelId + 1];
    }

    public StrategyModelRegistry registerPayoff(final PayoffModel model) {
        requireMutable();
        if (model == null) throw new NullPointerException("model is required");
        final int id = checkedId(model.modelId());
        if (payoffModels[id] != null) throw new IllegalStateException("duplicate payoff model ID");
        payoffModels[id] = model;
        return this;
    }

    public StrategyModelRegistry registerHedgeRatio(final HedgeRatioModel model) {
        requireMutable();
        if (model == null) throw new NullPointerException("model is required");
        final int id = checkedId(model.modelId());
        if (hedgeRatioModels[id] != null) {
            throw new IllegalStateException("duplicate hedge-ratio model ID");
        }
        hedgeRatioModels[id] = model;
        return this;
    }

    public StrategyModelRegistry registerCarryModel(final CarryModel model) {
        requireMutable();
        if (model == null) throw new NullPointerException("model is required");
        final int id = checkedId(model.modelId());
        if (carryModels[id] != null) throw new IllegalStateException("duplicate carry model ID");
        carryModels[id] = model;
        return this;
    }

    public StrategyModelRegistry registerSignalModel(final SignalModel model) {
        requireMutable();
        if (model == null) throw new NullPointerException("model is required");
        final int id = checkedId(model.modelId());
        if (signalModels[id] != null) throw new IllegalStateException("duplicate signal model ID");
        signalModels[id] = model;
        return this;
    }

    public StrategyModelRegistry registerExecutionPolicy(final ExecutionPolicyModel model) {
        requireMutable();
        if (model == null) throw new NullPointerException("model is required");
        final int id = checkedId(model.modelId());
        if (executionPolicies[id] != null) {
            throw new IllegalStateException("duplicate execution policy ID");
        }
        executionPolicies[id] = model;
        return this;
    }

    public StrategyModelRegistry freeze() {
        frozen = true;
        return this;
    }

    public boolean frozen() {
        return frozen;
    }

    public PayoffModel payoff(final int id) {
        return validId(id) ? payoffModels[id] : null;
    }

    public HedgeRatioModel hedgeRatio(final int id) {
        return validId(id) ? hedgeRatioModels[id] : null;
    }

    public CarryModel carry(final int id) {
        return validId(id) ? carryModels[id] : null;
    }

    public SignalModel signal(final int id) {
        return validId(id) ? signalModels[id] : null;
    }

    public ExecutionPolicyModel executionPolicy(final int id) {
        return validId(id) ? executionPolicies[id] : null;
    }

    private int checkedId(final int id) {
        if (!validId(id)) throw new IllegalArgumentException("model ID outside registry capacity");
        return id;
    }

    private boolean validId(final int id) {
        return id > 0 && id < payoffModels.length;
    }

    private void requireMutable() {
        if (frozen) throw new IllegalStateException("model registry is frozen");
    }
}
