package com.penguinsecure.basis.core.product;

/** Exact field-by-field startup comparison for certified and live instrument metadata. */
public final class InstrumentMetadataComparator {
    private InstrumentMetadataComparator() {}

    public static MetadataMismatch compare(
            final InstrumentDefinition expected, final InstrumentDefinition actual) {
        if (expected.instrumentId() != actual.instrumentId()) return MetadataMismatch.INSTRUMENT_ID;
        if (expected.venueId() != actual.venueId()) return MetadataMismatch.VENUE;
        if (expected.productFamily() != actual.productFamily())
            return MetadataMismatch.PRODUCT_FAMILY;
        if (expected.lifecycle() != actual.lifecycle()) return MetadataMismatch.LIFECYCLE;
        if (expected.baseCurrencyId() != actual.baseCurrencyId())
            return MetadataMismatch.BASE_CURRENCY;
        if (expected.quoteCurrencyId() != actual.quoteCurrencyId())
            return MetadataMismatch.QUOTE_CURRENCY;
        if (expected.settlementCurrencyId() != actual.settlementCurrencyId()) {
            return MetadataMismatch.SETTLEMENT_CURRENCY;
        }
        if (expected.collateralCurrencyId() != actual.collateralCurrencyId()) {
            return MetadataMismatch.COLLATERAL_CURRENCY;
        }
        if (expected.priceScale() != actual.priceScale()) return MetadataMismatch.PRICE_SCALE;
        if (expected.quantityScale() != actual.quantityScale())
            return MetadataMismatch.QUANTITY_SCALE;
        if (expected.multiplierScale() != actual.multiplierScale()) {
            return MetadataMismatch.MULTIPLIER_SCALE;
        }
        if (expected.tickSize() != actual.tickSize()) return MetadataMismatch.TICK_SIZE;
        if (expected.lotSize() != actual.lotSize()) return MetadataMismatch.LOT_SIZE;
        if (expected.minimumQuantity() != actual.minimumQuantity()) {
            return MetadataMismatch.MINIMUM_QUANTITY;
        }
        if (expected.maximumQuantity() != actual.maximumQuantity()) {
            return MetadataMismatch.MAXIMUM_QUANTITY;
        }
        if (expected.contractMultiplier() != actual.contractMultiplier()) {
            return MetadataMismatch.CONTRACT_MULTIPLIER;
        }
        if (expected.expiryEpochNanos() != actual.expiryEpochNanos())
            return MetadataMismatch.EXPIRY;
        if (expected.feeSourceId() != actual.feeSourceId()) return MetadataMismatch.FEE_SOURCE;
        return MetadataMismatch.NONE;
    }
}
