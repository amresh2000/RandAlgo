package com.penguinsecure.basis.core.time;

final class ManualClock implements MonotonicClock, EpochClock {
    private long nanos;

    ManualClock(long initialNanos) {
        nanos = initialNanos;
    }

    void advance(long deltaNanos) {
        nanos = Math.addExact(nanos, deltaNanos);
    }

    @Override
    public long nanoTime() {
        return nanos;
    }

    @Override
    public long epochNanos() {
        return nanos;
    }
}
