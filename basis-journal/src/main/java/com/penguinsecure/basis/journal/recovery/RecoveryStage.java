package com.penguinsecure.basis.journal.recovery;

/** Phase 8 restart sequence. There is intentionally no ARMED state. */
public enum RecoveryStage {
    BOOT,
    SNAPSHOT_LOADED,
    JOURNAL_REPLAYED,
    PRIVATE_CONNECTED,
    ORDERS_RECONCILED,
    POSITIONS_RECONCILED,
    BOOKS_TRUSTED,
    WARMED,
    DISARMED_READY,
    FAILED
}
