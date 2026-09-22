package com.penguinsecure.basis.venue.bybit.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.ssl.SslContextBuilder;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitNettyWebSocketConnectionTest {
    @Test
    void forwardsHandshakeCompletionExactlyOnce() {
        AtomicInteger ready = new AtomicInteger();
        BybitSessionListener listener =
                new BybitSessionListener() {
                    @Override
                    public void onTransportReady() {
                        ready.incrementAndGet();
                    }
                };
        EmbeddedChannel channel =
                new EmbeddedChannel(new BybitNettyWebSocketConnection.HandshakeEvents(listener));
        channel.pipeline()
                .fireUserEventTriggered(
                        WebSocketClientProtocolHandler.ClientHandshakeStateEvent
                                .HANDSHAKE_COMPLETE);
        assertEquals(1, ready.get());
        channel.finishAndReleaseAll();
    }

    @Test
    void requiresSecureEndpointAndSingleSessionBinding() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new BybitNettyWebSocketConnection(
                                    URI.create("ws://example.test/feed"),
                                    group,
                                    SslContextBuilder.forClient().build(),
                                    4096,
                                    1000,
                                    1000,
                                    ignored -> new ChannelInboundHandlerAdapter()));
            BybitNettyWebSocketConnection connection =
                    new BybitNettyWebSocketConnection(
                            URI.create("wss://example.test/feed"),
                            group,
                            SslContextBuilder.forClient().build(),
                            4096,
                            1000,
                            1000,
                            ignored -> new ChannelInboundHandlerAdapter());
            assertThrows(IllegalStateException.class, connection::connect);
            connection.bindSessionListener(BybitSessionListener.NOOP);
            assertThrows(
                    IllegalStateException.class,
                    () -> connection.bindSessionListener(BybitSessionListener.NOOP));
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
    }

    @Test
    void failedDialNotifiesSession() throws Exception {
        AtomicInteger disconnected = new AtomicInteger();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            BybitNettyWebSocketConnection connection =
                    new BybitNettyWebSocketConnection(
                            URI.create("wss://example.test/feed"),
                            group,
                            SslContextBuilder.forClient().build(),
                            4096,
                            250,
                            1000,
                            ignored -> new ChannelInboundHandlerAdapter());
            connection.bindSessionListener(
                    new BybitSessionListener() {
                        @Override
                        public boolean onDisconnected() {
                            disconnected.incrementAndGet();
                            return true;
                        }
                    });

            EmbeddedChannel failedChannel = new EmbeddedChannel();
            connection.handleConnectFailure(failedChannel);
            assertEquals(1, disconnected.get());
            failedChannel.finishAndReleaseAll();
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
    }
}
