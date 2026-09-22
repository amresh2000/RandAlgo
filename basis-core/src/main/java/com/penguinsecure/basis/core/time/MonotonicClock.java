package com.penguinsecure.basis.core.time;

@FunctionalInterface
public interface MonotonicClock {
    long nanoTime();
}
