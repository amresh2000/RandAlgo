package com.penguinsecure.basis.journal.replay;

/** Replay validation/apply result. */
public enum ReplayStatus {
    APPLIED,
    EXACT_DUPLICATE,
    SCHEMA_MISMATCH,
    UNKNOWN_TEMPLATE,
    SEQUENCE_GAP,
    CONFLICTING_DUPLICATE,
    INVALID_FRAME,
    OUTSIDE_RETAINED_RANGE
}
