package com.penguinsecure.basis.venue.bybit.marketdata;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.Utf8FrameValidator;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;

/** Installs explicit UTF-8 and aggregate-frame bounds before the feed handler. */
public final class BybitWebSocketPipeline {
    private BybitWebSocketPipeline() {}

    public static void install(
            final ChannelPipeline pipeline,
            final int maximumFrameBytes,
            final ChannelHandler handler) {
        if (maximumFrameBytes <= 0)
            throw new IllegalArgumentException("maximumFrameBytes must be positive");
        pipeline.addLast("frameLength", new BybitFrameLengthGuard(maximumFrameBytes));
        pipeline.addLast("utf8", new Utf8FrameValidator());
        pipeline.addLast("aggregate", new WebSocketFrameAggregator(maximumFrameBytes));
        pipeline.addLast("marketData", handler);
    }
}
