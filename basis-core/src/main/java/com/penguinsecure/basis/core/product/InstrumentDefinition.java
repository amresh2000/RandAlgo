package com.penguinsecure.basis.core.product;

import com.penguinsecure.basis.core.numeric.DecimalScale;

/** Immutable, dense instrument contract published to the execution core. */
public record InstrumentDefinition(
        int instrumentId,
        int venueId,
        ProductFamily productFamily,
        InstrumentLifecycle lifecycle,
        int baseCurrencyId,
        int quoteCurrencyId,
        int settlementCurrencyId,
        int collateralCurrencyId,
        int priceScale,
        int quantityScale,
        int multiplierScale,
        long tickSize,
        long lotSize,
        long minimumQuantity,
        long maximumQuantity,
        long contractMultiplier,
        long expiryEpochNanos,
        int feeSourceId) {

    public InstrumentDefinition {
        if (instrumentId <= 0 || venueId <= 0) {
            throw new IllegalArgumentException("instrumentId and venueId must be positive");
        }
        if (productFamily == null || lifecycle == null) {
            throw new NullPointerException("productFamily and lifecycle are required");
        }
        requireCurrency(baseCurrencyId, "baseCurrencyId");
        requireCurrency(quoteCurrencyId, "quoteCurrencyId");
        requireCurrency(settlementCurrencyId, "settlementCurrencyId");
        requireCurrency(collateralCurrencyId, "collateralCurrencyId");
        new DecimalScale(priceScale);
        new DecimalScale(quantityScale);
        new DecimalScale(multiplierScale);
        if (tickSize <= 0 || lotSize <= 0 || minimumQuantity <= 0) {
            throw new IllegalArgumentException("tick, lot, and minimum quantity must be positive");
        }
        if (maximumQuantity < minimumQuantity || contractMultiplier <= 0) {
            throw new IllegalArgumentException("invalid maximum quantity or contract multiplier");
        }
        if (productFamily.isDated() != (expiryEpochNanos > 0)) {
            throw new IllegalArgumentException("expiry must be present only for dated products");
        }
        if (feeSourceId <= 0) {
            throw new IllegalArgumentException("feeSourceId must be positive");
        }
    }

    private static void requireCurrency(final int currencyId, final String field) {
        if (currencyId <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
