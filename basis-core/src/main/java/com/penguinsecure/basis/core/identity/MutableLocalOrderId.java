package com.penguinsecure.basis.core.identity;

/** Caller-owned 128-bit order identifier storage. */
public final class MutableLocalOrderId {
    private long high;
    private long low;

    public long high() {
        return high;
    }

    public long low() {
        return low;
    }

    public MutableLocalOrderId set(long high, long low) {
        this.high = high;
        this.low = low;
        return this;
    }
}
