package com.penguinsecure.basis.journal.snapshot;

/** Verified snapshot bytes. Payload is returned only after the full file passes validation. */
public record SnapshotLoadResult(
        SnapshotLoadStatus status,
        SnapshotDescriptor descriptor,
        byte[] payload,
        String diagnostic) {
    public static SnapshotLoadResult failure(
            final SnapshotLoadStatus status, final String diagnostic) {
        return new SnapshotLoadResult(status, null, null, diagnostic);
    }
}
