package com.penguinsecure.basis.app.observability;

import org.agrona.concurrent.Agent;

/** Cold polling exporter; failures are counted and never propagated to core. */
public final class MetricsExportAgent implements Agent {
    private final RuntimeMetrics metrics;
    private final MetricsExporter exporter;
    private final MutableRuntimeMetricsSnapshot snapshot = new MutableRuntimeMetricsSnapshot();
    private long lastVersion = -1;

    public MetricsExportAgent(final RuntimeMetrics metrics, final MetricsExporter exporter) {
        if (metrics == null || exporter == null)
            throw new NullPointerException("dependencies are required");
        this.metrics = metrics;
        this.exporter = exporter;
    }

    @Override
    public int doWork() {
        if (!metrics.read(snapshot) || snapshot.version() == lastVersion) return 0;
        try {
            exporter.export(snapshot);
            lastVersion = snapshot.version();
        } catch (RuntimeException exception) {
            metrics.exporterFailed();
        }
        return 1;
    }

    @Override
    public String roleName() {
        return "basis-metrics-export";
    }
}
