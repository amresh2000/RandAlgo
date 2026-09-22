package com.penguinsecure.basis.venue.deribit.marketdata;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataParseStatus;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

@Tag("property")
final class DeribitBoundedBookParserProperties {
    private static final String VALID =
            "{\"data\":{\"timestamp\":1,\"instrument_name\":\"BTC-PERPETUAL\",\"change_id\":2,\"bids\":[[160,40]],\"asks\":[]}}";
    private final DeribitBoundedBookParser parser = new DeribitBoundedBookParser(() -> 1);
    private final MarketDataFeedProfile profile =
            new MarketDataFeedProfile(
                    1,
                    2,
                    2,
                    "BTC-PERPETUAL",
                    "book.BTC-PERPETUAL.none.20.100ms",
                    2,
                    3,
                    20,
                    4096,
                    true);
    private final MutableMarketDataEvent event = new MutableMarketDataEvent(20);

    @Property(tries = 1000)
    void everyProperPrefixIsRejected(@ForAll int candidate) {
        int cutoff = Math.floorMod(candidate, VALID.length());
        assertNotEquals(
                MarketDataParseStatus.OK,
                parser.parse(
                        Unpooled.wrappedBuffer(
                                VALID.substring(0, cutoff).getBytes(StandardCharsets.US_ASCII)),
                        profile,
                        1,
                        1,
                        1,
                        event));
    }

    @Property(tries = 1000)
    void inexactExponentNotationIsRejected(@ForAll int exponent) {
        String invalid = VALID.replace("160", "1.001e-" + Math.floorMod(exponent, 3));
        assertNotEquals(
                MarketDataParseStatus.OK,
                parser.parse(
                        Unpooled.wrappedBuffer(invalid.getBytes(StandardCharsets.US_ASCII)),
                        profile,
                        1,
                        1,
                        1,
                        event));
    }
}
