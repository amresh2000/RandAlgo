package com.penguinsecure.basis.journal.snapshot;

import com.penguinsecure.basis.core.recovery.CoreRecoveryState;

/** Fresh state is exposed only after the entire payload passes validation. */
public record SnapshotDecodeResult(
        SnapshotDecodeStatus status, CoreRecoveryState state, String diagnostic) {}
