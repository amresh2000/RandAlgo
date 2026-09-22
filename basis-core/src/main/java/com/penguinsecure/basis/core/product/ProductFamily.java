package com.penguinsecure.basis.core.product;

/** Certified product/payoff families. */
public enum ProductFamily {
    LINEAR_PERPETUAL(1),
    INVERSE_PERPETUAL(2),
    LINEAR_FUTURE(3),
    INVERSE_FUTURE(4);

    private final int code;

    ProductFamily(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean isInverse() {
        return this == INVERSE_PERPETUAL || this == INVERSE_FUTURE;
    }

    public boolean isDated() {
        return this == LINEAR_FUTURE || this == INVERSE_FUTURE;
    }
}
