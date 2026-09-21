package com.penguinsecure.basis.strategy.api.pricing;

/** Stable fail-closed outcome for opportunity evaluation. */
public enum PricingStatus {
    OPPORTUNITY(0),
    BELOW_THRESHOLD(1),
    INVALID_ARGUMENT(2),
    MODEL_NOT_REGISTERED(3),
    UNSUPPORTED_INSTRUMENT(4),
    BOOK_ROUTE_MISMATCH(5),
    BOOK_UNTRUSTED(6),
    BOOK_STALE(7),
    PRICE_NOT_TEMPORALLY_COHERENT(8),
    ECONOMIC_INPUT_INVALID(9),
    NO_EXECUTABLE_DEPTH(10),
    PARTIAL_EXECUTION(11),
    EXPOSURE_IMBALANCE(12),
    NUMERIC_FAILURE(13);

    private final int code;

    PricingStatus(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
