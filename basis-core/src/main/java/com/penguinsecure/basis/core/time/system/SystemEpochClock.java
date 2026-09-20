package com.penguinsecure.basis.core.time.system;

import com.penguinsecure.basis.core.time.EpochClock;
import java.time.Instant;

public final class SystemEpochClock implements EpochClock {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @Override
    public long epochNanos() {
        Instant now = Instant.now();
        return Math.addExact(
                Math.multiplyExact(now.getEpochSecond(), NANOS_PER_SECOND), now.getNano());
    }
}
