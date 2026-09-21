package com.penguinsecure.basis.strategy.api.pricing;

/** Caller-owned cross-leg timing evidence. */
public final class MutableTemporalEvidence {
    private long firstAgeNanos;
    private long secondAgeNanos;
    private long receiveSkewNanos;

    public long firstAgeNanos() {
        return firstAgeNanos;
    }

    public long secondAgeNanos() {
        return secondAgeNanos;
    }

    public long receiveSkewNanos() {
        return receiveSkewNanos;
    }

    void set(final long firstAge, final long secondAge, final long skew) {
        firstAgeNanos = firstAge;
        secondAgeNanos = secondAge;
        receiveSkewNanos = skew;
    }
}
