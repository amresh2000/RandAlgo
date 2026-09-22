package com.penguinsecure.basis.venue.bybit.order;

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

/** Event-loop handler for one independently authenticated Bybit private WebSocket. */
public final class BybitPrivateWebSocketHandler
        extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final BybitAuthenticatedSession session;
    private final BybitPrivateControlParser controlParser;
    private final BybitPrivateStreamParser privateParser;
    private final BybitOrderAgent orderAgent;
    private final VenueOrderFactSink facts;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final LaneHealthWord health;
    private final long producerEpoch;
    private final MutableBybitPrivateControl control = new MutableBybitPrivateControl();

    @SuppressWarnings("ParameterNumber")
    public BybitPrivateWebSocketHandler(
            final BybitAuthenticatedSession session,
            final BybitPrivateControlParser controlParser,
            final BybitPrivateStreamParser privateParser,
            final BybitOrderAgent orderAgent,
            final VenueOrderFactSink facts,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock,
            final LaneHealthWord health,
            final long producerEpoch) {
        if (session == null
                || controlParser == null
                || privateParser == null
                || orderAgent == null
                || facts == null
                || epochClock == null
                || monotonicClock == null
                || health == null)
            throw new NullPointerException("handler dependencies are required");
        this.session = session;
        this.controlParser = controlParser;
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
            if (contains(text, "\"topic\"")) {
                final BybitOrderParseStatus status =
                        privateParser.parse(
                                text.content(),
                                session.sessionGeneration(),
                                epochClock.epochNanos(),
                                monotonicClock.nanoTime(),
                                facts);
                if (status != BybitOrderParseStatus.OK) malformed();
            } else {
                final BybitOrderParseStatus status = controlParser.parse(text.content(), control);
                if (status != BybitOrderParseStatus.OK) {
                    malformed();
                } else if (control.kind() == BybitPrivateControlKind.AUTHENTICATED) {
                    session.onAuthenticationResult(true);
                } else if (control.kind() == BybitPrivateControlKind.AUTHENTICATION_FAILED) {
                    session.onAuthenticationResult(false);
                } else if (control.kind() == BybitPrivateControlKind.SUBSCRIBED) {
                    session.onSubscriptionAcknowledged();
                } else if (control.kind() == BybitPrivateControlKind.SUBSCRIPTION_FAILED) {
                    session.onDisconnected();
                }
            }
        } else if (frame instanceof PingWebSocketFrame ping) {
            context.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
        } else if (frame instanceof CloseWebSocketFrame) {
            orderAgent.onPrivateDisconnected();
            session.onDisconnected();
            context.close();
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext context) throws Exception {
        orderAgent.onPrivateDisconnected();
        session.onDisconnected();
        super.channelInactive(context);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
        orderAgent.onPrivateDisconnected();
        session.onDisconnected();
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

    private static boolean contains(final TextWebSocketFrame frame, final String ascii) {
        final int start = frame.content().readerIndex();
        final int limit = start + frame.content().readableBytes() - ascii.length();
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
