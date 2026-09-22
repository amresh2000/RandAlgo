package com.penguinsecure.basis.venue.bybit.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.venue.api.lane.LaneHealthSnapshot;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
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
final class BybitMarketDataHandlerTest {
    @Test
    void routesControlFramesWithoutPublishingOrCapturingThem() {
        AtomicInteger published = new AtomicInteger();
        AtomicInteger captured = new AtomicInteger();
        AtomicInteger activity = new AtomicInteger();
        AtomicInteger acknowledgements = new AtomicInteger();
        BybitSessionListener listener =
                new BybitSessionListener() {
                    @Override
                    public void onServerActivity() {
                        activity.incrementAndGet();
                    }

                    @Override
                    public void onSubscriptionAcknowledged() {
                        acknowledgements.incrementAndGet();
                    }
                };
        BybitMarketDataHandler handler =
                new BybitMarketDataHandler(
                        new MarketDataFeedProfile(
                                1, 1, 1, "X", "orderbook.50.X", 2, 3, 50, 4096, true),
                        1,
                        2,
                        () -> 10,
                        () -> 20,
                        event -> {
                            published.incrementAndGet();
                            return true;
                        },
                        (venue, epoch, mono, input, offset, length) -> {
                            captured.incrementAndGet();
                            return true;
                        },
                        new LaneHealthWord(),
                        listener);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.writeInbound(new TextWebSocketFrame("{\"success\" : true,\"op\":\"subscribe\"}"));
        channel.writeInbound(new TextWebSocketFrame("{\"op\":\"ping\",\"ret_msg\":\"pong\"}"));
        assertEquals(2, activity.get());
        assertEquals(1, acknowledgements.get());
        assertEquals(0, published.get());
        assertEquals(0, captured.get());
        channel.finishAndReleaseAll();
    }

    @Test
    void aggregatesFragmentedFramePublishesOnceAndReleasesInputs() {
        String json =
                "{\"topic\":\"orderbook.50.X\",\"type\":\"snapshot\",\"ts\":1,\"cts\":2,\"data\":{\"s\":\"X\",\"b\":[],\"a\":[],\"u\":3,\"seq\":4}}";
        int split = json.length() / 2;
        AtomicInteger published = new AtomicInteger();
        MarketDataFeedProfile profile =
                new MarketDataFeedProfile(1, 1, 1, "X", "orderbook.50.X", 2, 3, 50, 4096, true);
        BybitMarketDataHandler handler =
                new BybitMarketDataHandler(
                        profile,
                        1,
                        2,
                        () -> 10,
                        () -> 20,
                        event -> {
                            published.incrementAndGet();
                            assertEquals(4, event.venueSequence());
                            return true;
                        },
                        RawFrameSink.DISCARD,
                        new LaneHealthWord(),
                        BybitSessionListener.NOOP);
        EmbeddedChannel channel = new EmbeddedChannel();
        BybitWebSocketPipeline.install(channel.pipeline(), 4096, handler);
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

    @Test
    void oversizeAggregateClosesWithoutPublication() {
        AtomicInteger published = new AtomicInteger();
        MarketDataFeedProfile profile =
                new MarketDataFeedProfile(1, 1, 1, "X", "orderbook.50.X", 2, 3, 50, 256, true);
        LaneHealthWord health = new LaneHealthWord();
        BybitMarketDataHandler handler =
                new BybitMarketDataHandler(
                        profile,
                        1,
                        2,
                        () -> 10,
                        () -> 20,
                        event -> {
                            published.incrementAndGet();
                            return true;
                        },
                        RawFrameSink.DISCARD,
                        health,
                        BybitSessionListener.NOOP);
        EmbeddedChannel channel = new EmbeddedChannel();
        BybitWebSocketPipeline.install(channel.pipeline(), 32, handler);
        TextWebSocketFrame oversize =
                new TextWebSocketFrame(
                        false, 0, Unpooled.copiedBuffer("x".repeat(33), StandardCharsets.US_ASCII));
        channel.writeInbound(oversize);
        assertEquals(0, published.get());
        assertEquals(0, oversize.refCnt());
        LaneHealthSnapshot snapshot = new LaneHealthSnapshot();
        health.read(snapshot);
        assertEquals(LaneHealthState.DEGRADED, snapshot.state());
        channel.finishAndReleaseAll();
    }
}
