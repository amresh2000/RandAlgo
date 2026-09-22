package com.penguinsecure.basis.venue.deribit.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.RawFrameSink;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeribitMarketDataHandlerTest {
    @Test
    void routesAuthHeartbeatAndSubscriptionControlWithoutCapturingSecrets() {
        AtomicInteger captured = new AtomicInteger();
        AtomicInteger authenticated = new AtomicInteger();
        AtomicInteger acknowledgements = new AtomicInteger();
        AtomicInteger heartbeatTests = new AtomicInteger();
        DeribitSessionListener listener =
                new DeribitSessionListener() {
                    @Override
                    public void onAuthenticated() {
                        authenticated.incrementAndGet();
                    }

                    @Override
                    public void onSubscriptionAcknowledged() {
                        acknowledgements.incrementAndGet();
                    }

                    @Override
                    public void onHeartbeatTestRequest() {
                        heartbeatTests.incrementAndGet();
                    }
                };
        DeribitMarketDataHandler handler =
                new DeribitMarketDataHandler(
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
                                true),
                        1,
                        2,
                        () -> 10,
                        () -> 20,
                        event -> true,
                        (venue, epoch, mono, input, offset, length) -> {
                            captured.incrementAndGet();
                            return true;
                        },
                        new LaneHealthWord(),
                        listener);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.writeInbound(
                new TextWebSocketFrame("{\"id\":1,\"result\":{\"access_token\":\"secret\"}}"));
        channel.writeInbound(
                new TextWebSocketFrame(
                        "{\"method\":\"heartbeat\",\"params\":{\"type\":\"test_request\"}}"));
        channel.writeInbound(new TextWebSocketFrame("{\"id\" : 3,\"result\":\"ok\"}"));
        channel.writeInbound(new TextWebSocketFrame("{\"id\" : 2,\"result\":[\"book.X\"]}"));
        assertEquals(1, authenticated.get());
        assertEquals(1, heartbeatTests.get());
        assertEquals(1, acknowledgements.get());
        assertEquals(0, captured.get());
        channel.finishAndReleaseAll();
    }

    @Test
    void aggregatesCertifiedBoundedImageAndPublishesOnce() {
        String json =
                "{\"data\":{\"timestamp\":1,\"instrument_name\":\"BTC-PERPETUAL\",\"change_id\":2,\"bids\":[],\"asks\":[]}}";
        AtomicInteger published = new AtomicInteger();
        MarketDataFeedProfile profile =
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
        DeribitMarketDataHandler handler =
                new DeribitMarketDataHandler(
                        profile,
                        1,
                        2,
                        () -> 10,
                        () -> 20,
                        event -> {
                            published.incrementAndGet();
                            assertEquals(2, event.venueChangeId());
                            return true;
                        },
                        RawFrameSink.DISCARD,
                        new LaneHealthWord(),
                        DeribitSessionListener.NOOP);
        EmbeddedChannel channel = new EmbeddedChannel();
        DeribitWebSocketPipeline.install(channel.pipeline(), 4096, handler);
        int split = json.length() / 2;
        TextWebSocketFrame first =
                new TextWebSocketFrame(
                        false,
                        0,
                        Unpooled.copiedBuffer(json.substring(0, split), StandardCharsets.US_ASCII));
        ContinuationWebSocketFrame second =
                new ContinuationWebSocketFrame(
                        true,
                        0,
                        Unpooled.copiedBuffer(json.substring(split), StandardCharsets.US_ASCII));
        channel.writeInbound(first);
        channel.writeInbound(second);
        assertEquals(1, published.get());
        assertEquals(0, first.refCnt());
        assertEquals(0, second.refCnt());
        channel.finishAndReleaseAll();
    }
}
