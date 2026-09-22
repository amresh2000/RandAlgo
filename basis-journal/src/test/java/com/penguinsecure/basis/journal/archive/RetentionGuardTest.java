package com.penguinsecure.basis.journal.archive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RetentionGuardTest {
    @Test
    void requiresAllThreeStrictBoundariesAndStoppedSegment() {
        assertTrue(RetentionGuard.mayPurge(99, true, 100, 110, 120));
        assertFalse(RetentionGuard.mayPurge(100, true, 100, 110, 120));
        assertFalse(RetentionGuard.mayPurge(99, false, 100, 110, 120));
        assertFalse(RetentionGuard.mayPurge(110, true, 200, 110, 220));
    }
}
