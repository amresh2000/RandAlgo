package com.penguinsecure.basis.app.assembly;

import com.penguinsecure.basis.app.lifecycle.ExecutionCellLifecycle;
import com.penguinsecure.basis.app.lifecycle.ExecutionCellState;
import com.penguinsecure.basis.app.lifecycle.LifecycleStatus;
import com.penguinsecure.basis.core.time.MonotonicClock;
import org.agrona.concurrent.Agent;

/** Core-owned, deadline-bounded graceful shutdown state machine. */
public final class ShutdownCoordinator implements Agent {
    private final ExecutionCellLifecycle lifecycle;
    private final ShutdownPort port;
    private final MonotonicClock clock;
    private final long timeoutNanos;
    private long deadlineNanos;

    public ShutdownCoordinator(
            final ExecutionCellLifecycle lifecycle,
            final ShutdownPort port,
            final MonotonicClock clock,
            final long timeoutNanos) {
        if (lifecycle == null || port == null || clock == null) {
            throw new NullPointerException("dependencies are required");
        }
        if (timeoutNanos <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.lifecycle = lifecycle;
        this.port = port;
        this.clock = clock;
        this.timeoutNanos = timeoutNanos;
    }

    public boolean begin(final long controlGeneration) {
        final LifecycleStatus status = lifecycle.beginDrain(controlGeneration);
        if (status != LifecycleStatus.APPLIED) {
            return false;
        }
        deadlineNanos = clock.nanoTime() + timeoutNanos;
        return true;
    }

    @Override
    public int doWork() {
        final ExecutionCellState state = lifecycle.state();
        if (!shutdownInProgress(state)) {
            return 0;
        }
        if (clock.nanoTime() - deadlineNanos >= 0) {
            lifecycle.emergencyRequired();
            return 1;
        }
        return switch (state) {
            case DRAINING -> drain();
            case RECONCILING -> advance(port.reconcile(), ExecutionCellState.SNAPSHOTTING);
            case SNAPSHOTTING -> advance(port.snapshot(), ExecutionCellState.FLUSHING_JOURNAL);
            case FLUSHING_JOURNAL ->
                    advance(port.flushJournal(), ExecutionCellState.CLOSING_VENUES);
            case CLOSING_VENUES ->
                    advance(
                            port.closeOrderAndPrivateChannels(),
                            ExecutionCellState.CLOSING_INFRASTRUCTURE);
            case CLOSING_INFRASTRUCTURE ->
                    advance(port.closeMarketDataAndInfrastructure(), ExecutionCellState.STOPPED);
            default -> 0;
        };
    }

    @Override
    public String roleName() {
        return "basis-shutdown";
    }

    private int drain() {
        final ShutdownStepStatus status = port.drainAndResolve(deadlineNanos);
        if (status == ShutdownStepStatus.IN_PROGRESS) {
            return 0;
        }
        if (status == ShutdownStepStatus.FAILED) {
            lifecycle.emergencyRequired();
            return 1;
        }
        lifecycle.transitionShutdown(ExecutionCellState.RECONCILING);
        return 1;
    }

    private int advance(final boolean complete, final ExecutionCellState next) {
        if (!complete) {
            return 0;
        }
        lifecycle.transitionShutdown(next);
        return 1;
    }

    private static boolean shutdownInProgress(final ExecutionCellState state) {
        return state == ExecutionCellState.DRAINING
                || state == ExecutionCellState.RECONCILING
                || state == ExecutionCellState.SNAPSHOTTING
                || state == ExecutionCellState.FLUSHING_JOURNAL
                || state == ExecutionCellState.CLOSING_VENUES
                || state == ExecutionCellState.CLOSING_INFRASTRUCTURE;
    }
}
