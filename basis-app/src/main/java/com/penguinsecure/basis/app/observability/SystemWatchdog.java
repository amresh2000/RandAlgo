package com.penguinsecure.basis.app.observability;

/** Core-owned deterministic watchdog; exporters cannot alter its result. */
public final class SystemWatchdog {
    private final WatchdogConfig config;
    private WatchdogState state = WatchdogState.CORE_STALLED;

    public SystemWatchdog(final WatchdogConfig config) {
        if (config == null) throw new NullPointerException("config is required");
        this.config = config;
    }

    public WatchdogState observe(final WatchdogSample sample) {
        if (sample.coreProgressAgeNanos() > config.maximumCoreProgressAgeNanos())
            state = WatchdogState.CORE_STALLED;
        else if (sample.eventLoopProgressAgeNanos() > config.maximumEventLoopProgressAgeNanos())
            state = WatchdogState.EVENT_LOOP_STALLED;
        else if (sample.queueOccupancyPartsPerMillion()
                        >= config.maximumQueueOccupancyPartsPerMillion()
                || sample.oldestQueueAgeNanos() >= config.maximumQueueAgeNanos())
            state = WatchdogState.QUEUE_UNSAFE;
        else if (sample.marketDataAgeNanos() > config.maximumMarketDataAgeNanos())
            state = WatchdogState.DATA_STALE;
        else if (sample.absoluteClockOffsetNanos() > config.maximumClockOffsetNanos())
            state = WatchdogState.CLOCK_UNSAFE;
        else if (sample.diskUsableBytes() < config.minimumDiskUsableBytes())
            state = WatchdogState.DISK_UNSAFE;
        else if (!sample.venuesHealthy()) state = WatchdogState.VENUE_UNSAFE;
        else if (!sample.archiveHealthy()) state = WatchdogState.ARCHIVE_UNSAFE;
        else state = WatchdogState.HEALTHY;
        return state;
    }

    public WatchdogState state() {
        return state;
    }

    public boolean healthy() {
        return state == WatchdogState.HEALTHY;
    }
}
