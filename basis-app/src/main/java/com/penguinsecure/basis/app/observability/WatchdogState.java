package com.penguinsecure.basis.app.observability;

public enum WatchdogState {
    HEALTHY,
    CORE_STALLED,
    EVENT_LOOP_STALLED,
    QUEUE_UNSAFE,
    DATA_STALE,
    CLOCK_UNSAFE,
    DISK_UNSAFE,
    VENUE_UNSAFE,
    ARCHIVE_UNSAFE
}
