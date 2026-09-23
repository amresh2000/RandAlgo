package com.penguinsecure.basis.app.assembly;

import com.penguinsecure.basis.app.lifecycle.ExecutionCellLifecycle;
import com.penguinsecure.basis.app.lifecycle.LifecycleStatus;

/** Executes the fail-closed startup sequence and always stops before arming. */
public final class StartupCoordinator {
    private final ExecutionCellLifecycle lifecycle;
    private final StartupPort port;

    public StartupCoordinator(final ExecutionCellLifecycle lifecycle, final StartupPort port) {
        if (lifecycle == null || port == null) {
            throw new NullPointerException("dependencies are required");
        }
        this.lifecycle = lifecycle;
        this.port = port;
    }

    public LifecycleStatus start() {
        final LifecycleStatus recovery = lifecycle.beginRecovery();
        if (recovery != LifecycleStatus.APPLIED && recovery != LifecycleStatus.IDEMPOTENT) {
            return recovery;
        }
        if (!port.loadConfiguration()
                || !port.startArchive()
                || !port.loadVenueMetadata()
                || !port.reconcilePrivateState()
                || !port.synchronizeMarketData()
                || !port.warmUp()) {
            lifecycle.fail();
            return LifecycleStatus.NOT_READY;
        }
        final LifecycleStatus status = lifecycle.observeStartup(port.evidence());
        if (status != LifecycleStatus.APPLIED && status != LifecycleStatus.IDEMPOTENT) {
            lifecycle.fail();
        }
        return status;
    }
}
