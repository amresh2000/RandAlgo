package com.penguinsecure.basis.core.numeric;

/** Stable outcomes for expected numeric operations. */
public enum NumericStatus {
    OK(0),
    EMPTY(1),
    MALFORMED(2),
    SCALE_OUT_OF_RANGE(3),
    SCALE_LOSS(4),
    OVERFLOW(5),
    DIVIDE_BY_ZERO(6);

    private final int code;

    NumericStatus(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
