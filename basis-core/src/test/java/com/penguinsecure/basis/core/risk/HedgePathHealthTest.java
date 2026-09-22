package com.penguinsecure.basis.core.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class HedgePathHealthTest {
    @Test
    void unsafePathRequiresReconciliationAndHysteresis() {
        HedgePathHealth health =
                new HedgePathHealth(
                        new HedgePathHealthConfig(
                                50, 100, 500_000, 800_000, 100, 100, 100, 200, 2));
        HedgePathSample healthy =
                new HedgePathSample(0, 0, 8, 0, 0, true, true, true, 2, 1, 0, 10, 20, true);
        assertEquals(HedgePathHealthState.RECOVERING, health.observe(healthy));
        assertEquals(HedgePathHealthState.HEALTHY, health.observe(healthy));
        assertTrue(health.permitsInitiation());
        assertEquals(
                HedgePathHealthState.UNSAFE,
                health.observe(
                        new HedgePathSample(
                                0, 0, 8, 1, 0, true, true, true, 2, 1, 0, 10, 20, false)));
        assertFalse(health.permitsInitiation());
        assertEquals(
                HedgePathHealthState.RECOVERING,
                health.observe(
                        new HedgePathSample(
                                0, 0, 8, 0, 0, true, true, true, 2, 1, 0, 10, 20, false)));
    }
}
