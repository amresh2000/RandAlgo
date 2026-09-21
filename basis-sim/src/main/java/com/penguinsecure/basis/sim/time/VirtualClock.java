package com.penguinsecure.basis.sim.time;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;

/** Explicit dual-domain virtual clock which can only move forward. */
public final class VirtualClock implements EpochClock, MonotonicClock {
    private long epochNanos;
    private long monoNanos;

    public VirtualClock(final long initialEpochNanos, final long initialMonoNanos) {
        if (initialEpochNanos <= 0 || initialMonoNanos <= 0) {
            throw new IllegalArgumentException("clock origins must be positive");
        }
        epochNanos = initialEpochNanos;
        monoNanos = initialMonoNanos;
    }

    @Override
    public long epochNanos() {
        return epochNanos;
    }

    @Override
    public long nanoTime() {
        return monoNanos;
    }

    public void advanceTo(final long targetMonoNanos) {
        if (targetMonoNanos < monoNanos) {
            throw new IllegalArgumentException("virtual time cannot move backward");
        }
        final long delta = targetMonoNanos - monoNanos;
        epochNanos = Math.addExact(epochNanos, delta);
        monoNanos = targetMonoNanos;
    }

    public void advanceBy(final long deltaNanos) {
        if (deltaNanos < 0) throw new IllegalArgumentException("delta must be non-negative");
        advanceTo(Math.addExact(monoNanos, deltaNanos));
    }
}
