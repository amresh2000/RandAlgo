package com.penguinsecure.basis.venue.api.json;

/** Caller-owned primitive parse result. */
public final class MutableJsonLong {
    private long value;

    public long value() {
        return value;
    }

    void value(final long value) {
        this.value = value;
    }
}
