package com.penguinsecure.basis.venue.bybit.order;

/** Primitive cold-readable snapshot of the latest Bybit rate feedback. */
public final class BybitRateLimitState {
    private volatile long limit;
    private volatile long remaining;
    private volatile long resetTimestampMillis;
    private volatile long version;

    public void update(
            final long newLimit, final long newRemaining, final long newResetTimestampMillis) {
        if (newLimit < 0
                || newRemaining < 0
                || newRemaining > newLimit
                || newResetTimestampMillis < 0) {
            throw new IllegalArgumentException("invalid Bybit rate feedback");
        }
        limit = newLimit;
        remaining = newRemaining;
        resetTimestampMillis = newResetTimestampMillis;
        version++;
    }

    public long limit() {
        return limit;
    }

    public long remaining() {
        return remaining;
    }

    public long resetTimestampMillis() {
        return resetTimestampMillis;
    }

    public long version() {
        return version;
    }
}
