package com.penguinsecure.basis.journal.archive;

/** Safety predicate for stopped-segment purge. The active tail is never eligible. */
public final class RetentionGuard {
    private RetentionGuard() {}

    public static boolean mayPurge(
            final long segmentStopPosition,
            final boolean segmentStopped,
            final long activeRecordingStartPosition,
            final long verifiedCompatibleSnapshotPosition,
            final long projectorCheckpointPosition) {
        return segmentStopped
                && segmentStopPosition >= 0
                && segmentStopPosition < activeRecordingStartPosition
                && segmentStopPosition < verifiedCompatibleSnapshotPosition
                && segmentStopPosition < projectorCheckpointPosition;
    }
}
