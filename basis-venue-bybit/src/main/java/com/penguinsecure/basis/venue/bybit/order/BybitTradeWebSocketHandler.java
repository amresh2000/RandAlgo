package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/** Event-loop handler for one authenticated Bybit trade WebSocket. */
public final class BybitTradeWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final BybitAuthenticatedSession session;
    private final BybitOrderAgent agent;
    private final BybitTradeResponseParser parser;
    private final LaneHealthWord health;
    private final long producerEpoch;
    private final MutableBybitTradeResponse response = new MutableBybitTradeResponse();

    public BybitTradeWebSocketHandler(
            final BybitAuthenticatedSession session,
            final BybitOrderAgent agent,
            final BybitTradeResponseParser parser,
            final LaneHealthWord health,
            final long producerEpoch) {
        if (session == null || agent == null || parser == null || health == null) {
            throw new NullPointerException("handler dependencies are required");
        }
        this.session = session;
        this.agent = agent;
        this.parser = parser;
        this.health = health;
        this.producerEpoch = producerEpoch;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext context, final WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame text) {
            session.onServerActivity();
            final BybitOrderParseStatus status = parser.parse(text.content(), response);
            if (status != BybitOrderParseStatus.OK) {
                malformed();
                return;
            }
            if (response.kind() == BybitTradeResponseKind.AUTHENTICATED) {
                session.onAuthenticationResult(true);
            } else if (response.kind() == BybitTradeResponseKind.AUTHENTICATION_FAILED) {
                session.onAuthenticationResult(false);
            } else {
                final BybitOrderParseStatus responseStatus = agent.onResponse(response);
                if (responseStatus != BybitOrderParseStatus.OK
                        && responseStatus != BybitOrderParseStatus.IGNORED) malformed();
            }
        } else if (frame instanceof PingWebSocketFrame ping) {
            context.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
        } else if (frame instanceof CloseWebSocketFrame) {
            disconnected();
            context.close();
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext context) throws Exception {
        disconnected();
        super.channelInactive(context);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
        disconnected();
        context.close();
    }

    private void malformed() {
        health.publish(
                LaneHealthState.DEGRADED,
                VenueFailureReason.MALFORMED_INPUT,
                producerEpoch,
                session.sessionGeneration(),
                0);
    }

    private void disconnected() {
        agent.onDisconnected();
        session.onDisconnected();
    }
}
