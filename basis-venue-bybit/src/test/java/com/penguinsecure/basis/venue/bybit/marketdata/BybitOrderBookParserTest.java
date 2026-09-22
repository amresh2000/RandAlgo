package com.penguinsecure.basis.venue.bybit.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.product.ProductFamily;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataParseStatus;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitOrderBookParserTest {
    private final BybitOrderBookParser parser = new BybitOrderBookParser(() -> 700);
    private final MarketDataFeedProfile profile =
            new MarketDataFeedProfile(
                    9, 1, 101, "BTCUSDT", "orderbook.50.BTCUSDT", 2, 3, 50, 65_536, true);
    private final MutableMarketDataEvent event = new MutableMarketDataEvent(50);

    @Test
    void parsesArbitraryFieldOrderAndPreservesNativeEvidence() {
        String json =
                """
                {"unknown":{"nested":[1,true,null]},"data":{"seq":665,"a":[["30248.70","0"],["30249.30","0.892"]],"s":"BTCUSDT","u":177,"b":[["30247.20","30.028"]]},"cts":1687940967464,"ts":1687940967466,"type":"delta","topic":"orderbook.50.BTCUSDT"}
                """;
        assertEquals(MarketDataParseStatus.OK, parse(json));
        assertEquals(MarketDataEventKind.DELTA, event.kind());
        assertEquals(1687940967466L, event.venueTimestampMillis());
        assertEquals(1687940967464L, event.matchingEngineTimestampMillis());
        assertEquals(665, event.venueSequence());
        assertEquals(177, event.venueUpdateId());
        assertEquals(3_024_720, event.bidPriceTicks(0));
        assertEquals(30_028, event.bidQuantityLots(0));
        assertEquals(2, event.askCount());
        assertEquals(700, event.decodeCompleteMonoNanos());
    }

    @Test
    void rejectsDuplicateMissingAndUnsupportedNumbersWithoutPartialSuccess() {
        String duplicate =
                "{\"topic\":\"orderbook.50.BTCUSDT\",\"topic\":\"orderbook.50.BTCUSDT\",\"type\":\"snapshot\",\"ts\":1,\"cts\":1,\"data\":{\"s\":\"BTCUSDT\",\"b\":[],\"a\":[],\"u\":1,\"seq\":1}}";
        assertEquals(MarketDataParseStatus.DUPLICATE_REQUIRED_FIELD, parse(duplicate));
        String exponent =
                "{\"topic\":\"orderbook.50.BTCUSDT\",\"type\":\"snapshot\",\"ts\":1,\"cts\":1,\"data\":{\"s\":\"BTCUSDT\",\"b\":[[\"1e2\",\"1\"]],\"a\":[],\"u\":1,\"seq\":1}}";
        assertEquals(MarketDataParseStatus.INVALID_NUMBER, parse(exponent));
        String missing =
                "{\"topic\":\"orderbook.50.BTCUSDT\",\"type\":\"snapshot\",\"ts\":1,\"cts\":1,\"data\":{\"s\":\"BTCUSDT\",\"b\":[],\"a\":[],\"u\":1}}";
        assertEquals(MarketDataParseStatus.MISSING_REQUIRED_FIELD, parse(missing));
    }

    @Test
    void selectsEndpointFromProductMetadata() {
        assertTrue(
                BybitEndpoint.forProduct(ProductFamily.LINEAR_PERPETUAL)
                        .toString()
                        .endsWith("/linear"));
        assertTrue(
                BybitEndpoint.forProduct(ProductFamily.INVERSE_FUTURE)
                        .toString()
                        .endsWith("/inverse"));
    }

    @Test
    void rejectsFrameForDifferentConfiguredRoute() {
        String valid =
                "{\"topic\":\"orderbook.50.BTCUSDT\",\"type\":\"snapshot\",\"ts\":1,\"cts\":1,\"data\":{\"s\":\"BTCUSDT\",\"b\":[],\"a\":[],\"u\":1,\"seq\":1}}";
        assertEquals(
                MarketDataParseStatus.UNSUPPORTED_MESSAGE,
                parse(valid.replace("orderbook.50.BTCUSDT", "orderbook.1.BTCUSDT")));
        assertEquals(
                MarketDataParseStatus.UNSUPPORTED_MESSAGE,
                parse(valid.replace("\"s\":\"BTCUSDT\"", "\"s\":\"ETHUSDT\"")));
    }

    private MarketDataParseStatus parse(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.US_ASCII);
        return parser.parse(Unpooled.wrappedBuffer(bytes), profile, 3, 5, 6, event);
    }
}
