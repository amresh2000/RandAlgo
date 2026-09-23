package com.penguinsecure.basis.app.core;

import com.penguinsecure.basis.core.time.MonotonicClock;
import org.agrona.concurrent.Agent;

/** Priority-first duty cycle with bounded quotas and round-robin fairness. */
public final class CoreDutyCycle implements Agent {
    private final BoundedWorkSource health;
    private final BoundedWorkSource urgent;
    private final BoundedWorkSource[] highPriority;
    private final BoundedWorkSource[] fair;
    private final BoundedWorkSource[] background;
    private final MonotonicClock clock;
    private final int urgentQuota, highQuota, fairQuota, backgroundQuota;
    private final long starvationThresholdNanos;
    private final long[] starvationCounts;
    private int firstFair, firstBackground;

    @SuppressWarnings("ParameterNumber")
    public CoreDutyCycle(
            final BoundedWorkSource health,
            final BoundedWorkSource urgent,
            final BoundedWorkSource[] highPriority,
            final BoundedWorkSource[] fair,
            final BoundedWorkSource[] background,
            final MonotonicClock clock,
            final int urgentQuota,
            final int highQuota,
            final int fairQuota,
            final int backgroundQuota,
            final long starvationThresholdNanos) {
        if (health == null
                || urgent == null
                || highPriority == null
                || fair == null
                || fair.length == 0
                || background == null
                || clock == null
                || urgentQuota <= 0
                || highQuota <= 0
                || fairQuota <= 0
                || backgroundQuota <= 0
                || starvationThresholdNanos <= 0)
            throw new IllegalArgumentException("invalid duty-cycle configuration");
        this.health = health;
        this.urgent = urgent;
        this.highPriority = checked(highPriority);
        this.fair = checked(fair);
        this.background = checked(background);
        this.clock = clock;
        this.urgentQuota = urgentQuota;
        this.highQuota = highQuota;
        this.fairQuota = fairQuota;
        this.backgroundQuota = backgroundQuota;
        this.starvationThresholdNanos = starvationThresholdNanos;
        starvationCounts = new long[fair.length];
    }

    @Override
    public int doWork() {
        int work = health.doWork(1);
        work += urgent.doWork(urgentQuota);
        for (BoundedWorkSource source : highPriority) work += source.doWork(highQuota);
        final long now = clock.nanoTime();
        for (int step = 0; step < fair.length; step++) {
            final int index = (firstFair + step) % fair.length;
            final BoundedWorkSource source = fair[index];
            final int done = source.doWork(fairQuota);
            work += done;
            if (source.oldestAgeNanos(now) >= starvationThresholdNanos) starvationCounts[index]++;
        }
        firstFair = (firstFair + 1) % fair.length;
        if (background.length > 0) {
            work += background[firstBackground].doWork(backgroundQuota);
            firstBackground = (firstBackground + 1) % background.length;
        }
        return work;
    }

    public long starvationCount(final int fairSourceIndex) {
        return starvationCounts[fairSourceIndex];
    }

    @Override
    public String roleName() {
        return "basis-core-duty-cycle";
    }

    private static BoundedWorkSource[] checked(final BoundedWorkSource[] sources) {
        final BoundedWorkSource[] copy = sources.clone();
        for (BoundedWorkSource source : copy)
            if (source == null) throw new NullPointerException("work source is null");
        return copy;
    }
}
