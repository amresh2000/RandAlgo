package com.penguinsecure.basis.journal.recovery;

import com.penguinsecure.basis.core.recovery.CoreRecoveryState;
import com.penguinsecure.basis.core.recovery.CoreStateRestorer;
import com.penguinsecure.basis.core.recovery.RecoveryValidationStatus;

/** Recovery invariant seam kept independent of transport adapters. */
public final class RecoveryInvariantChecker {
    public boolean valid(final CoreRecoveryState state) {
        return CoreStateRestorer.validate(state) == RecoveryValidationStatus.VALID;
    }
}
