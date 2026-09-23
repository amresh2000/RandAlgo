package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactSink;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/** Netty boundary for the independently authenticated Deribit private socket. */
public final class DeribitPrivateWebSocketHandler
        extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final DeribitAuthenticatedSession session;
    private final DeribitJsonRpcResponseParser responseParser;
    private final DeribitPrivateStreamParser privateParser;
    private final DeribitOrderAgent orderAgent;
    private final VenueOrderFactSink facts;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final LaneHealthWord health;
    private final long producerEpoch;
    private final MutableDeribitResponse response = new MutableDeribitResponse();

    @SuppressWarnings("ParameterNumber")
    public DeribitPrivateWebSocketHandler(
            final DeribitAuthenticatedSession session,
            final DeribitJsonRpcResponseParser responseParser,
            final DeribitPrivateStreamParser privateParser,
            final DeribitOrderAgent orderAgent,
            final VenueOrderFactSink facts,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock,
            final LaneHealthWord health,
            final long producerEpoch) {
        if (session == null
                || responseParser == null
                || privateParser == null
                || orderAgent == null
                || facts == null
                || epochClock == null
                || monotonicClock == null
                || health == null) throw new NullPointerException("dependencies are required");
        this.session = session;
        this.responseParser = responseParser;
        this.privateParser = privateParser;
        this.orderAgent = orderAgent;
        this.facts = facts;
        this.epochClock = epochClock;
        this.monotonicClock = monotonicClock;
        this.health = health;
        this.producerEpoch = producerEpoch;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext context, final WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame text) {
            session.onServerActivity();
            if (contains(text, "\"channel\":\"user.")) {
                final DeribitOrderParseStatus status =
                        privateParser.parse(
                                text.content(),
                                session.sessionGeneration(),
                                epochClock.epochNanos(),
                                monotonicClock.nanoTime(),
                                facts);
                if (status != DeribitOrderParseStatus.OK
                        && status != DeribitOrderParseStatus.IGNORED) malformed();
            } else {
                final DeribitOrderParseStatus status =
                        responseParser.parse(text.content(), response);
                if (status != DeribitOrderParseStatus.OK || !session.onResponse(response))
                    malformed();
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
        orderAgent.onPrivateDisconnected();
        session.onDisconnected();
    }

    private static boolean contains(final TextWebSocketFrame frame, final String ascii) {
        final int start = frame.content().readerIndex(),
                limit = start + frame.content().readableBytes() - ascii.length();
        for (int offset = start; offset <= limit; offset++) {
            int index = 0;
            while (index < ascii.length()
                    && frame.content().getByte(offset + index) == (byte) ascii.charAt(index))
                index++;
            if (index == ascii.length()) return true;
        }
        return false;
    }
}
