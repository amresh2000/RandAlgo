package com.penguinsecure.basis.core.time.system;

import com.penguinsecure.basis.core.time.MonotonicClock;

public final class SystemMonotonicClock implements MonotonicClock {
    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}
