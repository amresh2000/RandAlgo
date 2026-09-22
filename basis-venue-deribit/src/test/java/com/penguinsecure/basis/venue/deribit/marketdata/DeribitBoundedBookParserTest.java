package com.penguinsecure.basis.venue.deribit.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataParseStatus;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeribitBoundedBookParserTest {
    private final DeribitBoundedBookParser parser = new DeribitBoundedBookParser(() -> 900);
    private final MutableMarketDataEvent event = new MutableMarketDataEvent(20);

    @Test
    void rejectsUncertifiedBoundedImageProfile() {
        assertEquals(MarketDataParseStatus.UNSUPPORTED_PROFILE, parse(false, validFrame()));
        assertEquals(0, event.instrumentId());
    }

    @Test
    void parsesWrappedBoundedImageAndPreservesChangeId() {
        assertEquals(MarketDataParseStatus.OK, parse(true, validFrame()));
        assertEquals(MarketDataEventKind.IMAGE, event.kind());
        assertEquals(1554375447971L, event.venueTimestampMillis());
        assertEquals(109615, event.venueChangeId());
        assertEquals(16_000, event.bidPriceTicks(0));
        assertEquals(40_000, event.bidQuantityLots(0));
        assertEquals(16_100, event.askPriceTicks(0));
        assertEquals(900, event.decodeCompleteMonoNanos());
    }

    @Test
    void rejectsUnknownChannelAndMissingDataFields() {
        String badChannel = validFrame().replace("book.BTC", "ticker.BTC");
        assertEquals(MarketDataParseStatus.UNSUPPORTED_MESSAGE, parse(true, badChannel));
        String missing = validFrame().replace(",\"asks\":[[161,20]]", "");
        assertEquals(MarketDataParseStatus.MISSING_REQUIRED_FIELD, parse(true, missing));
    }

    @Test
    void rejectsFrameForDifferentConfiguredInstrument() {
        assertEquals(
                MarketDataParseStatus.UNSUPPORTED_MESSAGE,
                parse(
                        true,
                        validFrame()
                                .replace("instrument_name\":\"BTC", "instrument_name\":\"ETH")));
        assertEquals(
                MarketDataParseStatus.UNSUPPORTED_MESSAGE,
                parse(true, validFrame().replace("none.20.100ms", "none.10.100ms")));
    }

    private MarketDataParseStatus parse(boolean certified, String json) {
        MarketDataFeedProfile profile =
                new MarketDataFeedProfile(
                        8,
                        2,
                        202,
                        "BTC-PERPETUAL",
                        "book.BTC-PERPETUAL.none.20.100ms",
                        2,
                        3,
                        20,
                        65_536,
                        certified);
        byte[] bytes = json.getBytes(StandardCharsets.US_ASCII);
        return parser.parse(Unpooled.wrappedBuffer(bytes), profile, 4, 5, 6, event);
    }

    private static String validFrame() {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"subscription\",\"params\":{\"channel\":\"book.BTC-PERPETUAL.none.20.100ms\",\"data\":{\"timestamp\":1554375447971,\"instrument_name\":\"BTC-PERPETUAL\",\"change_id\":109615,\"bids\":[[160,40]],\"asks\":[[161,20]]}}}";
    }
}
