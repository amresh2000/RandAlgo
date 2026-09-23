package com.penguinsecure.basis.app.observability;

@FunctionalInterface
public interface MetricsExporter {
    void export(MutableRuntimeMetricsSnapshot snapshot);
}
