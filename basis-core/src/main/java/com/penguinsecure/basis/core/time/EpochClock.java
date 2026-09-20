package com.penguinsecure.basis.core.time;

@FunctionalInterface
public interface EpochClock {
    long epochNanos();
}
