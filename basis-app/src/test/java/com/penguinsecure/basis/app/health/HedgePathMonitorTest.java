package com.penguinsecure.basis.app.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.app.observability.StageLatencyHistogram;
import com.penguinsecure.basis.core.risk.HedgePathHealth;
import com.penguinsecure.basis.core.risk.HedgePathHealthConfig;
import com.penguinsecure.basis.core.risk.HedgePathHealthState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class HedgePathMonitorTest {
    @Test
    void latencyWindowDegradesThenRecoversWithHysteresis() {
        final StageLatencyHistogram histogram = new StageLatencyHistogram();
        final HedgePathMonitor monitor =
                new HedgePathMonitor(
                        new HedgePathHealth(
                                new HedgePathHealthConfig(
                                        100, 200, 500_000, 900_000, 100, 100, 10, 20, 2)),
                        histogram);
        final HedgePathObservation healthy =
                new HedgePathObservation(1, 0, 10, 0, 1, true, true, true, 1, 1, 0, true);

        histogram.record(9);
        assertEquals(HedgePathHealthState.DEGRADED, monitor.observe(healthy));
        assertEquals(HedgePathHealthState.RECOVERING, monitor.observe(healthy));
        assertEquals(HedgePathHealthState.HEALTHY, monitor.observe(healthy));
        assertTrue(monitor.permitsInitiation());
    }
}
