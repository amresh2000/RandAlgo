package com.penguinsecure.basis.journal.snapshot;

/** Logical snapshot payload decode result. */
public enum SnapshotDecodeStatus {
    DECODED,
    CORRUPT,
    CAPACITY_MISMATCH,
    INVALID_STATE
}
