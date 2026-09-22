package com.penguinsecure.basis.journal.snapshot;

/** Snapshot boundaries are publishable only after the archive has durably covered them. */
public final class SnapshotEligibility {
    private SnapshotEligibility() {}

    public static boolean isCovered(
            final long snapshotJournalPosition, final long archiveRecordingPosition) {
        return snapshotJournalPosition >= 0 && archiveRecordingPosition >= snapshotJournalPosition;
    }
}
