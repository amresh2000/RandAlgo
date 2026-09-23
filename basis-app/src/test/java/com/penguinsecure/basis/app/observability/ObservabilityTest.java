package com.penguinsecure.basis.app.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class ObservabilityTest {
    @Test
    void watchdogFailsClosedForEveryUnsafeSignal() {
        final SystemWatchdog watchdog =
                new SystemWatchdog(new WatchdogConfig(10, 20, 900_000, 30, 40, 50, 60));

        assertEquals(
                WatchdogState.HEALTHY,
                watchdog.observe(new WatchdogSample(1, 2, 3, 4, 5, 6, 70, true, true)));
        assertEquals(
                WatchdogState.CORE_STALLED,
                watchdog.observe(new WatchdogSample(11, 2, 3, 4, 5, 6, 70, true, true)));
        assertFalse(watchdog.healthy());
        assertEquals(
                WatchdogState.ARCHIVE_UNSAFE,
                watchdog.observe(new WatchdogSample(1, 2, 3, 4, 5, 6, 70, true, false)));
    }

    @Test
    void histogramReturnsConservativeBucketBound() {
        final StageLatencyHistogram histogram = new StageLatencyHistogram();
        histogram.record(8);
        histogram.record(9);

        assertEquals(15, histogram.percentile(990_000));
        assertEquals(9, histogram.maximum());
    }

    @Test
    void exporterFailureIsContainedAndCounted() {
        final RuntimeMetrics metrics = new RuntimeMetrics();
        metrics.publish(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);
        final MetricsExportAgent failing =
                new MetricsExportAgent(
                        metrics,
                        snapshot -> {
                            throw new IllegalStateException("offline");
                        });

        assertEquals(1, failing.doWork());
        final MutableRuntimeMetricsSnapshot snapshot = new MutableRuntimeMetricsSnapshot();
        assertTrue(metrics.read(snapshot));
        assertEquals(1, snapshot.exporterFailures());
        assertEquals(11, snapshot.decisions());
        assertEquals(16, snapshot.marketDataToWriteP99Nanos());
        assertEquals(21, snapshot.gcPauseNanos());
    }
}
