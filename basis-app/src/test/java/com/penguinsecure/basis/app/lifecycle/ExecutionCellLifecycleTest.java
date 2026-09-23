package com.penguinsecure.basis.app.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.penguinsecure.basis.journal.recovery.RecoveryStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class ExecutionCellLifecycleTest {
    @Test
    void cannotArmUntilAllStartupEvidenceIsReady() {
        final ExecutionCellLifecycle lifecycle = new ExecutionCellLifecycle();

        assertEquals(LifecycleStatus.APPLIED, lifecycle.beginRecovery());
        assertEquals(
                LifecycleStatus.NOT_READY,
                lifecycle.observeStartup(
                        new StartupEvidence(RecoveryStage.WARMED, 7, true, true, true)));
        assertEquals(LifecycleStatus.INVALID_STATE, lifecycle.arm(7, 1));
        assertFalse(lifecycle.armed());

        assertEquals(
                LifecycleStatus.APPLIED,
                lifecycle.observeStartup(
                        new StartupEvidence(RecoveryStage.DISARMED_READY, 7, true, true, true)));
        assertEquals(LifecycleStatus.STALE_GENERATION, lifecycle.arm(6, 1));
        assertEquals(LifecycleStatus.APPLIED, lifecycle.arm(7, 1));
    }

    @Test
    void controlGenerationsCannotBeReusedForAStateChange() {
        final ExecutionCellLifecycle lifecycle = readyLifecycle();

        assertEquals(LifecycleStatus.APPLIED, lifecycle.arm(7, 2));
        assertEquals(LifecycleStatus.STALE_GENERATION, lifecycle.disarm(2));
        assertEquals(LifecycleStatus.APPLIED, lifecycle.disarm(3));
        assertEquals(LifecycleStatus.IDEMPOTENT, lifecycle.disarm(3));
    }

    private static ExecutionCellLifecycle readyLifecycle() {
        final ExecutionCellLifecycle lifecycle = new ExecutionCellLifecycle();
        lifecycle.beginRecovery();
        lifecycle.observeStartup(
                new StartupEvidence(RecoveryStage.DISARMED_READY, 7, true, true, true));
        return lifecycle;
    }
}
