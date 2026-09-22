package com.penguinsecure.basis.app.marketdata;

import java.util.Arrays;

/** Fixed-memory rolling sample window; sorting is confined to the reporting path. */
final class LatencyWindow {
    private final long[] values;
    private int size;
    private int next;
    private long totalSamples;

    LatencyWindow(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        values = new long[capacity];
    }

    void record(final long nanos) {
        if (nanos < 0) throw new IllegalArgumentException("latency cannot be negative");
        values[next] = nanos;
        next = (next + 1) % values.length;
        if (size < values.length) size++;
        totalSamples++;
    }

    Snapshot snapshot() {
        if (size == 0) return new Snapshot(totalSamples, 0, 0, 0, 0, 0, 0);
        final long[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        return new Snapshot(
                totalSamples,
                size,
                percentile(sorted, 50.0),
                percentile(sorted, 90.0),
                percentile(sorted, 99.0),
                percentile(sorted, 99.9),
                sorted[size - 1]);
    }

    private static long percentile(final long[] sorted, final double percentile) {
        final int rank = (int) Math.ceil((percentile / 100.0) * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    record Snapshot(
            long totalSamples,
            int retainedSamples,
            long p50Nanos,
            long p90Nanos,
            long p99Nanos,
            long p999Nanos,
            long maximumNanos) {}
}
