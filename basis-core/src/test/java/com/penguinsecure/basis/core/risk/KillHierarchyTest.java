package com.penguinsecure.basis.core.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class KillHierarchyTest {
    @Test
    void resetRequiresStrictlyNewerGeneration() {
        KillHierarchy kills = new KillHierarchy(16);
        assertEquals(KillUpdateStatus.APPLIED, kills.kill(KillScope.STRATEGY, 7, 3));
        assertEquals(KillUpdateStatus.IDEMPOTENT, kills.kill(KillScope.STRATEGY, 7, 3));
        assertEquals(KillUpdateStatus.STALE_GENERATION, kills.reset(KillScope.STRATEGY, 7, 3));
        assertTrue(kills.isKilled(KillScope.STRATEGY, 7));
        assertEquals(KillUpdateStatus.APPLIED, kills.reset(KillScope.STRATEGY, 7, 4));
        assertFalse(kills.isKilled(KillScope.STRATEGY, 7));
    }
}
