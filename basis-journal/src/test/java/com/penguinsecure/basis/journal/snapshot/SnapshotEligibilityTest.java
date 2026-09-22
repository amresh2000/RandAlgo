package com.penguinsecure.basis.journal.snapshot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SnapshotEligibilityTest {
    @Test
    void requiresRecordingPositionToCoverBoundary() {
        assertFalse(SnapshotEligibility.isCovered(101, 100));
        assertTrue(SnapshotEligibility.isCovered(101, 101));
        assertFalse(SnapshotEligibility.isCovered(-1, 101));
    }
}
