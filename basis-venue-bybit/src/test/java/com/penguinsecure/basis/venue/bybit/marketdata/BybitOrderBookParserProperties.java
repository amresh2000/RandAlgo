package com.penguinsecure.basis.venue.bybit.marketdata;

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
final class BybitOrderBookParserProperties {
    private static final String VALID =
            "{\"topic\":\"orderbook.50.X\",\"type\":\"snapshot\",\"ts\":1,\"cts\":1,\"data\":{\"s\":\"X\",\"b\":[[\"100.00\",\"1.000\"]],\"a\":[],\"u\":1,\"seq\":1}}";
    private final BybitOrderBookParser parser = new BybitOrderBookParser(() -> 1);
    private final MarketDataFeedProfile profile =
            new MarketDataFeedProfile(1, 1, 1, "X", "orderbook.50.X", 2, 3, 50, 4096, true);
    private final MutableMarketDataEvent event = new MutableMarketDataEvent(50);

    @Property(tries = 1000)
    void everyProperPrefixIsRejected(@ForAll int candidate) {
        int cutoff = Math.floorMod(candidate, VALID.length());
        assertNotEquals(MarketDataParseStatus.OK, parse(VALID.substring(0, cutoff)));
    }

    @Property(tries = 1000)
    void exponentNotationIsRejected(@ForAll int exponent) {
        String invalid = VALID.replace("100.00", "1e" + exponent);
        assertNotEquals(MarketDataParseStatus.OK, parse(invalid));
    }

    private MarketDataParseStatus parse(final String json) {
        return parser.parse(
                Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.US_ASCII)),
                profile,
                1,
                1,
                1,
                event);
    }
}
