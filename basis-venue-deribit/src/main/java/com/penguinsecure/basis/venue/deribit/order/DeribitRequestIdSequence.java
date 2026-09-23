package com.penguinsecure.basis.venue.deribit.order;

/** Monotonic process-lifetime JSON-RPC identifier sequence. */
public final class DeribitRequestIdSequence {
    private long last;

    public DeribitRequestIdSequence(final long initialValue) {
        if (initialValue < 0)
            throw new IllegalArgumentException("initial value must be non-negative");
        last = initialValue;
    }

    public long next() {
        if (last == Long.MAX_VALUE) throw new IllegalStateException("request IDs exhausted");
        return ++last;
    }

    public long last() {
        return last;
    }
}
