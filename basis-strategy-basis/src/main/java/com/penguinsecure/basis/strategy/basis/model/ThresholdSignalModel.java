package com.penguinsecure.basis.strategy.basis.model;

import com.penguinsecure.basis.strategy.api.model.SignalModel;

/** Emits when conservative net edge reaches the configured entry threshold. */
public final class ThresholdSignalModel implements SignalModel {
    private final int modelId;

    public ThresholdSignalModel(final int modelId) {
        if (modelId <= 0) throw new IllegalArgumentException("modelId must be positive");
        this.modelId = modelId;
    }

    @Override
    public int modelId() {
        return modelId;
    }

    @Override
    public boolean isEntry(final long netEdgeRate, final long entryThreshold) {
        return netEdgeRate >= entryThreshold;
    }
}
