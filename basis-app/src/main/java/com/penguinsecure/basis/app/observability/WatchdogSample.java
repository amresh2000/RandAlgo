package com.penguinsecure.basis.app.observability;

/** One same-clock system safety sample. */
public record WatchdogSample(
        long coreProgressAgeNanos,
        long eventLoopProgressAgeNanos,
        int queueOccupancyPartsPerMillion,
        long oldestQueueAgeNanos,
        long marketDataAgeNanos,
        long absoluteClockOffsetNanos,
        long diskUsableBytes,
        boolean venuesHealthy,
        boolean archiveHealthy) {
    public WatchdogSample {
        if (coreProgressAgeNanos < 0
                || eventLoopProgressAgeNanos < 0
                || queueOccupancyPartsPerMillion < 0
                || queueOccupancyPartsPerMillion > 1_000_000
                || oldestQueueAgeNanos < 0
                || marketDataAgeNanos < 0
                || absoluteClockOffsetNanos < 0
                || diskUsableBytes < 0) throw new IllegalArgumentException("invalid sample");
    }
}
