package com.penguinsecure.basis.journal.snapshot;

/** Snapshot scan/load result. */
public enum SnapshotLoadStatus {
    LOADED,
    NOT_FOUND,
    INCOMPATIBLE,
    CORRUPT,
    IO_FAILURE
}
