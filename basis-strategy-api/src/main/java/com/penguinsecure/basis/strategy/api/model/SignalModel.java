package com.penguinsecure.basis.strategy.api.model;

/** Compiled entry signal evaluated after complete economic decomposition. */
public interface SignalModel {
    int modelId();

    boolean isEntry(long netEdgeRate, long entryThreshold);
}
