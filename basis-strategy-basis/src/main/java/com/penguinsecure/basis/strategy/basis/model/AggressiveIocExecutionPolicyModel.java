package com.penguinsecure.basis.strategy.basis.model;

import com.penguinsecure.basis.strategy.api.model.ExecutionPolicyModel;

/** Registered identity for the v1 aggressive initiation/immediate hedge policy. */
public final class AggressiveIocExecutionPolicyModel implements ExecutionPolicyModel {
    private final int modelId;

    public AggressiveIocExecutionPolicyModel(final int modelId) {
        if (modelId <= 0) throw new IllegalArgumentException("modelId must be positive");
        this.modelId = modelId;
    }

    @Override
    public int modelId() {
        return modelId;
    }
}
