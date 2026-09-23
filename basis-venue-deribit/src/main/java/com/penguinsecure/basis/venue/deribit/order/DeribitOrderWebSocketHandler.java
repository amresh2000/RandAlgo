package com.penguinsecure.basis.venue.deribit.order;

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

/** Netty boundary for the independent authenticated Deribit order socket. */
public final class DeribitOrderWebSocketHandler
        extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final DeribitAuthenticatedSession session;
    private final DeribitOrderAgent agent;
    private final DeribitJsonRpcResponseParser parser;
    private final LaneHealthWord health;
    private final long producerEpoch;
    private final MutableDeribitResponse response = new MutableDeribitResponse();

    public DeribitOrderWebSocketHandler(
            final DeribitAuthenticatedSession session,
            final DeribitOrderAgent agent,
            final DeribitJsonRpcResponseParser parser,
            final LaneHealthWord health,
            final long producerEpoch) {
        if (session == null || agent == null || parser == null || health == null)
            throw new NullPointerException("dependencies are required");
        this.session = session;
        this.agent = agent;
        this.parser = parser;
        this.health = health;
        this.producerEpoch = producerEpoch;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext context, final WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame text) {
            final DeribitOrderParseStatus status = parser.parse(text.content(), response);
            if (status != DeribitOrderParseStatus.OK) {
                malformed();
                return;
            }
            if (!session.onResponse(response)) {
                final DeribitOrderParseStatus result = agent.onResponse(response);
                if (result != DeribitOrderParseStatus.OK
                        && result != DeribitOrderParseStatus.IGNORED) malformed();
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
