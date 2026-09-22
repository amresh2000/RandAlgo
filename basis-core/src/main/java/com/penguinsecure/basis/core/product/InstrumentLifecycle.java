package com.penguinsecure.basis.core.product;

/** Venue instrument lifecycle admitted by startup validation. */
public enum InstrumentLifecycle {
    PRE_LAUNCH(1),
    TRADING(2),
    SUSPENDED(3),
    SETTLED(4),
    EXPIRED(5);

    private final int code;

    InstrumentLifecycle(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
