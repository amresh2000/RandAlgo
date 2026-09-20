package com.penguinsecure.basis.core.numeric;

/** Stable semantic units used at primitive-valued component boundaries. */
public enum NumericUnit {
    PRICE_TICKS(1),
    QUANTITY_LOTS(2),
    NATIVE_AMOUNT(3),
    NOTIONAL(4),
    RATE(5),
    BASIS_POINTS(6),
    CURRENCY_AMOUNT(7),
    EPOCH_NANOS(8),
    MONOTONIC_NANOS(9),
    GENERATION(10);

    private final int code;

    NumericUnit(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
