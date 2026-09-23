package com.penguinsecure.basis.app.core;

/** Nonblocking core-owned source with bounded work and same-clock queue age. */
public interface BoundedWorkSource {
    int doWork(int limit);

    long oldestAgeNanos(long nowMonoNanos);
}
