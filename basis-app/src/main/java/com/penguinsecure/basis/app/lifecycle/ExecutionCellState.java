package com.penguinsecure.basis.app.lifecycle;

/** Authoritative process state; restart never transitions directly to ARMED. */
public enum ExecutionCellState {
    BOOT,
    RECOVERING,
    DISARMED_READY,
    ARMED,
    DRAINING,
    RECONCILING,
    SNAPSHOTTING,
    FLUSHING_JOURNAL,
    CLOSING_VENUES,
    CLOSING_INFRASTRUCTURE,
    STOPPED,
    EMERGENCY_REQUIRED,
    FAILED
}
