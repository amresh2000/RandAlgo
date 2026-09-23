package com.penguinsecure.basis.app.lifecycle;

import com.penguinsecure.basis.journal.recovery.RecoveryStage;

/** Same-sample startup evidence consumed by the lifecycle owner. */
public record StartupEvidence(
        RecoveryStage recoveryStage,
        long configurationGeneration,
        boolean archiveHealthy,
        boolean watchdogHealthy,
        boolean hedgePathsHealthy) {
    public StartupEvidence {
        if (recoveryStage == null || configurationGeneration < 0)
            throw new IllegalArgumentException("invalid startup evidence");
    }

    public boolean ready() {
        return recoveryStage == RecoveryStage.DISARMED_READY
                && configurationGeneration > 0
                && archiveHealthy
                && watchdogHealthy
                && hedgePathsHealthy;
    }
}
