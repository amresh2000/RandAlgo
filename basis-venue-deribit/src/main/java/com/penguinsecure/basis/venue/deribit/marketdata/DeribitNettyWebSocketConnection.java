package com.penguinsecure.basis.venue.deribit.marketdata;

import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import java.net.URI;
import java.util.function.Function;

/** Concrete TLS/NIO WebSocket control; the supplied event-loop group remains caller-owned. */
public final class DeribitNettyWebSocketConnection
        implements VenueConnectionControl, DeribitSessionListener {
    private static final int HTTP_AGGREGATE_BYTES = 16 * 1024;

    private final URI endpoint;
    private final String host;
    private final int port;
    private final EventLoopGroup eventLoopGroup;
    private final SslContext sslContext;
    private final int maximumFrameBytes;
    private final int connectTimeoutMillis;
    private final long handshakeTimeoutMillis;
    private final Function<DeribitSessionListener, ? extends ChannelHandler> handlerFactory;
    private DeribitSessionListener sessionListener = DeribitSessionListener.NOOP;
    private boolean listenerBound;
    private volatile Channel channel;

    public DeribitNettyWebSocketConnection(
            final URI endpoint,
            final EventLoopGroup eventLoopGroup,
            final SslContext sslContext,
            final int maximumFrameBytes,
            final int connectTimeoutMillis,
            final long handshakeTimeoutMillis,
            final Function<DeribitSessionListener, ? extends ChannelHandler> handlerFactory) {
        if (endpoint == null
                || eventLoopGroup == null
                || sslContext == null
                || handlerFactory == null)
            throw new NullPointerException("dependencies are required");
        if (!"wss".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("a host-only wss endpoint is required");
        }
        if (maximumFrameBytes < 256 || connectTimeoutMillis <= 0 || handshakeTimeoutMillis <= 0) {
            throw new IllegalArgumentException("invalid transport bounds");
        }
        this.endpoint = endpoint;
        this.host = endpoint.getHost();
        this.port = endpoint.getPort() < 0 ? 443 : endpoint.getPort();
        this.eventLoopGroup = eventLoopGroup;
        this.sslContext = sslContext;
        this.maximumFrameBytes = maximumFrameBytes;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        this.handlerFactory = handlerFactory;
    }

    public void bindSessionListener(final DeribitSessionListener listener) {
        if (listener == null) throw new NullPointerException("listener is required");
        if (listenerBound || channel != null)
            throw new IllegalStateException("listener already bound");
        sessionListener = listener;
        listenerBound = true;
    }

    @Override
    public void connect() {
        if (!listenerBound) throw new IllegalStateException("session listener is not bound");
        final Channel current = channel;
        if (current != null && current.isOpen()) return;
        final Bootstrap bootstrap =
                new Bootstrap()
                        .group(eventLoopGroup)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                        .handler(
                                new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(final SocketChannel socket) {
                                        socket.pipeline()
                                                .addLast(
                                                        "tls",
                                                        sslContext.newHandler(
                                                                socket.alloc(), host, port))
                                                .addLast("http", new HttpClientCodec())
                                                .addLast(
                                                        "http-aggregate",
                                                        new HttpObjectAggregator(
                                                                HTTP_AGGREGATE_BYTES))
                                                .addLast(
                                                        "websocket",
                                                        new WebSocketClientProtocolHandler(
                                                                websocketConfig()))
                                                .addLast(
                                                        "handshake-events",
                                                        new HandshakeEvents(
                                                                DeribitNettyWebSocketConnection
                                                                        .this));
                                        DeribitWebSocketPipeline.install(
                                                socket.pipeline(),
                                                maximumFrameBytes,
                                                handlerFactory.apply(
                                                        DeribitNettyWebSocketConnection.this));
                                    }
                                });
        final ChannelFuture connectFuture = bootstrap.connect(host, port);
        final Channel candidate = connectFuture.channel();
        channel = candidate;
        candidate.closeFuture().addListener(ignored -> clearChannel(candidate));
        connectFuture.addListener(
                completed -> {
                    if (!completed.isSuccess()) {
                        handleConnectFailure(candidate);
                    }
                });
    }

    @Override
    public void sendText(final CharSequence payload) {
        if (payload == null) throw new NullPointerException("payload is required");
        final Channel current = channel;
        if (current == null || !current.isActive())
            throw new IllegalStateException("WebSocket is not active");
        current.writeAndFlush(new TextWebSocketFrame(payload.toString()));
    }

    @Override
    public void close() {
        final Channel current = channel;
        if (current != null) current.close();
    }

    @Override
    public void onTransportReady() {
        sessionListener.onTransportReady();
    }

    @Override
    public void onServerActivity() {
        sessionListener.onServerActivity();
    }

    @Override
    public void onAuthenticated() {
        sessionListener.onAuthenticated();
    }

    @Override
    public void onSubscriptionAcknowledged() {
        sessionListener.onSubscriptionAcknowledged();
    }

    @Override
    public void onHeartbeatTestRequest() {
        sessionListener.onHeartbeatTestRequest();
    }

    @Override
    public boolean onDisconnected() {
        return sessionListener.onDisconnected();
    }

    private WebSocketClientProtocolConfig websocketConfig() {
        return WebSocketClientProtocolConfig.newBuilder()
                .webSocketUri(endpoint)
                .version(WebSocketVersion.V13)
                .allowExtensions(false)
                .maxFramePayloadLength(maximumFrameBytes)
                .handleCloseFrames(false)
                .dropPongFrames(false)
                .handshakeTimeoutMillis(handshakeTimeoutMillis)
                .build();
    }

    private void clearChannel(final Channel candidate) {
        if (channel == candidate) channel = null;
    }

    void handleConnectFailure(final Channel candidate) {
        clearChannel(candidate);
        onDisconnected();
    }

    static final class HandshakeEvents extends ChannelInboundHandlerAdapter {
        private final DeribitSessionListener listener;

        HandshakeEvents(final DeribitSessionListener listener) {
            this.listener = listener;
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext context, final Object event) {
            if (event
                    == WebSocketClientProtocolHandler.ClientHandshakeStateEvent
                            .HANDSHAKE_COMPLETE) {
                listener.onTransportReady();
            }
            context.fireUserEventTriggered(event);
        }
    }
}
