package com.penguinsecure.basis.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class LatencyWindowTest {
    @Test
    void reportsPercentilesOverBoundedRollingWindow() {
        LatencyWindow window = new LatencyWindow(4);
        window.record(10);
        window.record(20);
        window.record(30);
        window.record(40);
        window.record(50);

        LatencyWindow.Snapshot snapshot = window.snapshot();
        assertEquals(5, snapshot.totalSamples());
        assertEquals(4, snapshot.retainedSamples());
        assertEquals(30, snapshot.p50Nanos());
        assertEquals(50, snapshot.p90Nanos());
        assertEquals(50, snapshot.p99Nanos());
        assertEquals(50, snapshot.maximumNanos());
    }

    @Test
    void rejectsNegativeLatency() {
        LatencyWindow window = new LatencyWindow(1);
        assertThrows(IllegalArgumentException.class, () -> window.record(-1));
    }
}
