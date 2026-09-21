package com.penguinsecure.basis.strategy.api.pricing;

/** Direction of the paired execution relative to the strategy's configured legs. */
public enum BasisDirection {
    BUY_FIRST_SELL_SECOND(1),
    SELL_FIRST_BUY_SECOND(2);

    private final int code;

    BasisDirection(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
