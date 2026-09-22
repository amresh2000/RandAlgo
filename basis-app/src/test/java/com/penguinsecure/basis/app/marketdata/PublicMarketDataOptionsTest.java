package com.penguinsecure.basis.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class PublicMarketDataOptionsTest {
    @Test
    void defaultsToBothVenuesForOneMinute() {
        PublicMarketDataOptions options = PublicMarketDataOptions.parse(new String[0]);

        assertEquals(PublicMarketDataOptions.VenueSelection.BOTH, options.venues());
        assertEquals(60, options.durationSeconds());
        assertEquals(5, options.reportSeconds());
    }

    @Test
    void parsesSupportedOverridesAndRejectsUnknownArguments() {
        PublicMarketDataOptions options =
                PublicMarketDataOptions.parse(
                        new String[] {
                            "--venue=deribit", "--duration-seconds=0", "--report-seconds=10"
                        });

        assertEquals(PublicMarketDataOptions.VenueSelection.DERIBIT, options.venues());
        assertEquals(0, options.durationSeconds());
        assertEquals(10, options.reportSeconds());
        assertThrows(
                IllegalArgumentException.class,
                () -> PublicMarketDataOptions.parse(new String[] {"--credentials=x"}));
    }
}
