package com.penguinsecure.basis.journal.recovery;

/** Result of supplying evidence to recovery. */
public enum RecoveryStatus {
    ADVANCED,
    WAITING_FOR_EVIDENCE,
    INVALID_PREDECESSOR,
    FAILED
}
