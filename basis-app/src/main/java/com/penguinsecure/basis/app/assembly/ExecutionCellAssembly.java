package com.penguinsecure.basis.app.assembly;

import com.penguinsecure.basis.app.core.CoreDutyCycle;
import com.penguinsecure.basis.app.lifecycle.ExecutionCellLifecycle;
import com.penguinsecure.basis.app.lifecycle.LifecycleStatus;
import com.penguinsecure.basis.app.observability.MetricsExportAgent;
import com.penguinsecure.basis.app.operator.OperatorGateway;
import org.agrona.concurrent.Agent;

/** Explicit process composition root; construction has no reflection or service locator. */
public final class ExecutionCellAssembly {
    private final ExecutionCellLifecycle lifecycle;
    private final StartupCoordinator startup;
    private final CoreDutyCycle core;
    private final ShutdownCoordinator shutdown;
    private final OperatorGateway operatorGateway;
    private final MetricsExportAgent metricsExporter;

    public ExecutionCellAssembly(
            final ExecutionCellLifecycle lifecycle,
            final StartupCoordinator startup,
            final CoreDutyCycle core,
            final ShutdownCoordinator shutdown,
            final OperatorGateway operatorGateway,
            final MetricsExportAgent metricsExporter) {
        if (lifecycle == null
                || startup == null
                || core == null
                || shutdown == null
                || operatorGateway == null
                || metricsExporter == null) {
            throw new NullPointerException("dependencies are required");
        }
        this.lifecycle = lifecycle;
        this.startup = startup;
        this.core = core;
        this.shutdown = shutdown;
        this.operatorGateway = operatorGateway;
        this.metricsExporter = metricsExporter;
    }

    public LifecycleStatus start() {
        return startup.start();
    }

    public Agent coreAgent() {
        return core;
    }

    public Agent shutdownAgent() {
        return shutdown;
    }

    public Agent metricsAgent() {
        return metricsExporter;
    }

    public ExecutionCellLifecycle lifecycle() {
        return lifecycle;
    }

    public OperatorGateway operatorGateway() {
        return operatorGateway;
    }
}
