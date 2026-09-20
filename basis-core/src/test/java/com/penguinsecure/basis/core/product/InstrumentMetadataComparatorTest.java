package com.penguinsecure.basis.core.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class InstrumentMetadataComparatorTest {
    @Test
    void acceptsExactMetadataAndRejectsDriftWithSpecificReason() {
        InstrumentDefinition expected = inversePerpetual(1, 1, 100_000_000);

        assertEquals(
                MetadataMismatch.NONE, InstrumentMetadataComparator.compare(expected, expected));
        assertEquals(
                MetadataMismatch.VENUE,
                InstrumentMetadataComparator.compare(
                        expected, inversePerpetual(1, 2, 100_000_000)));
        assertEquals(
                MetadataMismatch.CONTRACT_MULTIPLIER,
                InstrumentMetadataComparator.compare(expected, inversePerpetual(1, 1, 10)));
    }

    private static InstrumentDefinition inversePerpetual(
            int instrumentId, int venueId, long multiplier) {
        return new InstrumentDefinition(
                instrumentId,
                venueId,
                ProductFamily.INVERSE_PERPETUAL,
                InstrumentLifecycle.TRADING,
                1,
                2,
                1,
                1,
                1,
                0,
                8,
                1,
                1,
                1,
                1_000_000,
                multiplier,
                0,
                1);
    }
}
