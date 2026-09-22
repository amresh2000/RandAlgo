package com.penguinsecure.basis.core.product;

/** Stable fail-closed reason returned when live metadata differs from certification. */
public enum MetadataMismatch {
    NONE(0),
    INSTRUMENT_ID(1),
    VENUE(2),
    PRODUCT_FAMILY(3),
    LIFECYCLE(4),
    BASE_CURRENCY(5),
    QUOTE_CURRENCY(6),
    SETTLEMENT_CURRENCY(7),
    COLLATERAL_CURRENCY(8),
    PRICE_SCALE(9),
    QUANTITY_SCALE(10),
    MULTIPLIER_SCALE(11),
    TICK_SIZE(12),
    LOT_SIZE(13),
    MINIMUM_QUANTITY(14),
    MAXIMUM_QUANTITY(15),
    CONTRACT_MULTIPLIER(16),
    EXPIRY(17),
    FEE_SOURCE(18);

    private final int code;

    MetadataMismatch(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
