package com.penguinsecure.basis.app.observability;

public record WatchdogConfig(
        long maximumCoreProgressAgeNanos,
        long maximumEventLoopProgressAgeNanos,
        int maximumQueueOccupancyPartsPerMillion,
        long maximumQueueAgeNanos,
        long maximumMarketDataAgeNanos,
        long maximumClockOffsetNanos,
        long minimumDiskUsableBytes) {
    public WatchdogConfig {
        if (maximumCoreProgressAgeNanos <= 0
                || maximumEventLoopProgressAgeNanos <= 0
                || maximumQueueOccupancyPartsPerMillion <= 0
                || maximumQueueOccupancyPartsPerMillion > 1_000_000
                || maximumQueueAgeNanos <= 0
                || maximumMarketDataAgeNanos <= 0
                || maximumClockOffsetNanos <= 0
                || minimumDiskUsableBytes <= 0)
            throw new IllegalArgumentException("invalid watchdog config");
    }
}
