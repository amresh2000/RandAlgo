package com.penguinsecure.basis.sim.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataParseStatus;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import com.penguinsecure.basis.venue.bybit.marketdata.BybitOrderBookParser;
import com.penguinsecure.basis.venue.deribit.marketdata.DeribitBoundedBookParser;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("replay")
final class MarketDataFixtureReplayTest {
    @Test
    void officialSanitizedExamplesReplayToStableNormalizedDigest() throws IOException {
        String first = replayDigest();
        String second = replayDigest();
        assertEquals(first, second);
        assertEquals("0ea70418afa659d0fc5a0d18e14588bce303e0be72ff24952eca79f00e90a027", first);
    }

    private static String replayDigest() throws IOException {
        MutableMarketDataEvent event = new MutableMarketDataEvent(50);
        StableMarketDataDigest digest = new StableMarketDataDigest();
        byte[] bybit = resource("/wire/market-data/bybit-orderbook-50-snapshot.json");
        MarketDataFeedProfile bybitProfile =
                new MarketDataFeedProfile(
                        1, 1, 101, "BTCUSDT", "orderbook.50.BTCUSDT", 2, 3, 50, 65_536, true);
        assertEquals(
                MarketDataParseStatus.OK,
                new BybitOrderBookParser(() -> 30)
                        .parse(Unpooled.wrappedBuffer(bybit), bybitProfile, 1, 10, 20, event));
        digest.update(event);

        byte[] deribit = resource("/wire/market-data/deribit-bounded-20-image.json");
        MarketDataFeedProfile deribitProfile =
                new MarketDataFeedProfile(
                        2,
                        2,
                        202,
                        "BTC-PERPETUAL",
                        "book.BTC-PERPETUAL.none.20.100ms",
                        2,
                        3,
                        20,
                        65_536,
                        true);
        assertEquals(
                MarketDataParseStatus.OK,
                new DeribitBoundedBookParser(() -> 31)
                        .parse(Unpooled.wrappedBuffer(deribit), deribitProfile, 1, 11, 21, event));
        digest.update(event);
        return digest.finishHex();
    }

    private static byte[] resource(final String name) throws IOException {
        try (InputStream input = MarketDataFixtureReplayTest.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("missing resource " + name);
            return input.readAllBytes();
        }
    }
}
