package com.penguinsecure.basis.journal.snapshot;

/** Durable identity and compatibility boundary for a snapshot. */
public record SnapshotDescriptor(
        int formatVersion,
        int schemaId,
        int schemaVersion,
        long buildGeneration,
        long configurationGeneration,
        long catalogGeneration,
        long snapshotId,
        long recordingId,
        long recordingPosition,
        long captureEpochNanos) {
    public SnapshotDescriptor {
        if (formatVersion <= 0
                || schemaId <= 0
                || schemaVersion < 0
                || buildGeneration <= 0
                || configurationGeneration <= 0
                || catalogGeneration <= 0
                || snapshotId <= 0
                || recordingId < 0
                || recordingPosition < 0
                || captureEpochNanos <= 0) {
            throw new IllegalArgumentException("invalid snapshot descriptor");
        }
    }
}
