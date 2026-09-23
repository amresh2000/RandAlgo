package com.penguinsecure.basis.app.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.app.lifecycle.ExecutionCellLifecycle;
import com.penguinsecure.basis.app.lifecycle.ExecutionCellState;
import com.penguinsecure.basis.app.lifecycle.LifecycleStatus;
import com.penguinsecure.basis.app.lifecycle.StartupEvidence;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.journal.recovery.RecoveryStage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class AssemblyLifecycleTest {
    @Test
    void startupRunsInRequiredOrderAndStopsDisarmed() {
        final List<String> order = new ArrayList<>();
        final ExecutionCellLifecycle lifecycle = new ExecutionCellLifecycle();
        final StartupCoordinator startup =
                new StartupCoordinator(lifecycle, new RecordingStartup(order));

        assertEquals(LifecycleStatus.APPLIED, startup.start());
        assertEquals(
                List.of(
                        "configuration",
                        "archive",
                        "metadata",
                        "private-reconciliation",
                        "market-data-sync",
                        "warm-up",
                        "evidence"),
                order);
        assertEquals(ExecutionCellState.DISARMED_READY, lifecycle.state());
        assertFalse(lifecycle.armed());
    }

    @Test
    void gracefulShutdownIsOrderedAndDeadlineFailureRequiresEmergencyAction() {
        final ExecutionCellLifecycle lifecycle = readyLifecycle();
        final MutableClock clock = new MutableClock();
        final RecordingShutdown port = new RecordingShutdown();
        final ShutdownCoordinator shutdown = new ShutdownCoordinator(lifecycle, port, clock, 100);

        assertTrue(shutdown.begin(1));
        for (int index = 0; index < 6; index++) {
            assertEquals(1, shutdown.doWork());
        }
        assertEquals(ExecutionCellState.STOPPED, lifecycle.state());
        assertEquals(
                List.of("drain", "reconcile", "snapshot", "flush", "venues", "infrastructure"),
                port.order);

        final ExecutionCellLifecycle secondLifecycle = readyLifecycle();
        final RecordingShutdown blocked = new RecordingShutdown();
        blocked.drainStatus = ShutdownStepStatus.IN_PROGRESS;
        final ShutdownCoordinator deadline =
                new ShutdownCoordinator(secondLifecycle, blocked, clock, 10);
        assertTrue(deadline.begin(1));
        assertEquals(0, deadline.doWork());
        clock.now = 10;
        assertEquals(1, deadline.doWork());
        assertEquals(ExecutionCellState.EMERGENCY_REQUIRED, secondLifecycle.state());
    }

    private static ExecutionCellLifecycle readyLifecycle() {
        final ExecutionCellLifecycle lifecycle = new ExecutionCellLifecycle();
        lifecycle.beginRecovery();
        lifecycle.observeStartup(
                new StartupEvidence(RecoveryStage.DISARMED_READY, 1, true, true, true));
        return lifecycle;
    }

    private static final class RecordingStartup implements StartupPort {
        private final List<String> order;

        private RecordingStartup(final List<String> order) {
            this.order = order;
        }

        @Override
        public boolean loadConfiguration() {
            return add("configuration");
        }

        @Override
        public boolean startArchive() {
            return add("archive");
        }

        @Override
        public boolean loadVenueMetadata() {
            return add("metadata");
        }

        @Override
        public boolean reconcilePrivateState() {
            return add("private-reconciliation");
        }

        @Override
        public boolean synchronizeMarketData() {
            return add("market-data-sync");
        }

        @Override
        public boolean warmUp() {
            return add("warm-up");
        }

        @Override
        public StartupEvidence evidence() {
            order.add("evidence");
            return new StartupEvidence(RecoveryStage.DISARMED_READY, 1, true, true, true);
        }

        private boolean add(final String step) {
            order.add(step);
            return true;
        }
    }

    private static final class RecordingShutdown implements ShutdownPort {
        private final List<String> order = new ArrayList<>();
        private ShutdownStepStatus drainStatus = ShutdownStepStatus.COMPLETE;

        @Override
        public ShutdownStepStatus drainAndResolve(final long deadlineNanos) {
            order.add("drain");
            return drainStatus;
        }

        @Override
        public boolean reconcile() {
            return add("reconcile");
        }

        @Override
        public boolean snapshot() {
            return add("snapshot");
        }

        @Override
        public boolean flushJournal() {
            return add("flush");
        }

        @Override
        public boolean closeOrderAndPrivateChannels() {
            return add("venues");
        }

        @Override
        public boolean closeMarketDataAndInfrastructure() {
            return add("infrastructure");
        }

        private boolean add(final String step) {
            order.add(step);
            return true;
        }
    }

    private static final class MutableClock implements MonotonicClock {
        private long now;

        @Override
        public long nanoTime() {
            return now;
        }
    }
}
