package com.penguinsecure.basis.venue.deribit.marketdata;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataParseStatus;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataSink;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import com.penguinsecure.basis.venue.api.marketdata.RawFrameSink;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/** Netty event-loop owner for validated Deribit bounded book images. */
public final class DeribitMarketDataHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final MarketDataFeedProfile profile;
    private final long sessionGeneration;
    private final long producerEpoch;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final DeribitBoundedBookParser parser;
    private final MarketDataSink sink;
    private final RawFrameSink rawFrameSink;
    private final LaneHealthWord healthWord;
    private final DeribitSessionListener sessionListener;
    private final MutableMarketDataEvent event;
    private final DeribitByteBufInput rawInput = new DeribitByteBufInput();

    public DeribitMarketDataHandler(
            final MarketDataFeedProfile profile,
            final long sessionGeneration,
            final long producerEpoch,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock,
            final MarketDataSink sink,
            final RawFrameSink rawFrameSink,
            final LaneHealthWord healthWord,
            final DeribitSessionListener sessionListener) {
        if (profile == null
                || epochClock == null
                || monotonicClock == null
                || sink == null
                || rawFrameSink == null
                || healthWord == null
                || sessionListener == null)
            throw new NullPointerException("arguments are required");
        this.profile = profile;
        this.sessionGeneration = sessionGeneration;
        this.producerEpoch = producerEpoch;
        this.epochClock = epochClock;
        this.monotonicClock = monotonicClock;
        this.sink = sink;
        this.rawFrameSink = rawFrameSink;
        this.healthWord = healthWord;
        this.sessionListener = sessionListener;
        this.parser = new DeribitBoundedBookParser(monotonicClock);
        this.event = new MutableMarketDataEvent(profile.maximumDepth());
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext context, final WebSocketFrame frame) {
        final long receiveMonoNanos = monotonicClock.nanoTime();
        final long receiveEpochNanos = epochClock.epochNanos();
        if (frame instanceof TextWebSocketFrame text) {
            rawInput.wrap(text.content());
            sessionListener.onServerActivity();
            if (contains(text, "access_token")) {
                sessionListener.onAuthenticated();
                return;
            }
            if (contains(text, "\"method\"") && contains(text, "heartbeat")) {
                if (contains(text, "test_request")) sessionListener.onHeartbeatTestRequest();
                return;
            }
            if (!contains(text, "\"bids\"") || !contains(text, "\"asks\"")) {
                if (hasNumericField(text, "id", 2) && contains(text, "\"result\"")) {
                    sessionListener.onSubscriptionAcknowledged();
                } else if (contains(text, "\"id\"") && contains(text, "\"result\"")) {
                    // Acknowledgement for authentication refresh, heartbeat setup, or public/test.
                } else {
                    publishMalformed(MarketDataParseStatus.MALFORMED);
                }
                return;
            }
            rawFrameSink.offer(
                    profile.venueId(),
                    receiveEpochNanos,
                    receiveMonoNanos,
                    rawInput,
                    text.content().readerIndex(),
                    text.content().readableBytes());
            final MarketDataParseStatus status =
                    parser.parse(
                            text.content(),
                            profile,
                            sessionGeneration,
                            receiveEpochNanos,
                            receiveMonoNanos,
                            event);
            if (status != MarketDataParseStatus.OK) {
                publishMalformed(status);
            } else if (!sink.publish(event)) {
                healthWord.publish(
                        LaneHealthState.OVERFLOW,
                        VenueFailureReason.RING_OVERFLOW,
                        producerEpoch,
                        sessionGeneration,
                        profile.instrumentId());
            }
        } else if (frame instanceof PingWebSocketFrame ping) {
            context.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
        } else if (frame instanceof CloseWebSocketFrame) {
            publishDisconnected();
            context.close();
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext context) throws Exception {
        publishDisconnected();
        super.channelInactive(context);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
        publishDisconnected();
        context.close();
    }

    private void publishDisconnected() {
        if (sessionListener.onDisconnected()) return;
        healthWord.publish(
                LaneHealthState.DEGRADED,
                VenueFailureReason.DISCONNECTED,
                producerEpoch,
                sessionGeneration,
                profile.instrumentId());
    }

    private void publishMalformed(final MarketDataParseStatus status) {
        healthWord.publish(
                LaneHealthState.DEGRADED,
                status == MarketDataParseStatus.UNSUPPORTED_PROFILE
                        ? VenueFailureReason.CAPABILITY_NOT_CERTIFIED
                        : VenueFailureReason.MALFORMED_INPUT,
                producerEpoch,
                sessionGeneration,
                profile.instrumentId());
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

    private static boolean hasNumericField(
            final TextWebSocketFrame frame, final String field, final int expected) {
        final String quotedField = '"' + field + '"';
        final int start = frame.content().readerIndex();
        final int end = start + frame.content().readableBytes();
        for (int offset = start; offset + quotedField.length() < end; offset++) {
            int fieldIndex = 0;
            while (fieldIndex < quotedField.length()
                    && frame.content().getByte(offset + fieldIndex)
                            == (byte) quotedField.charAt(fieldIndex)) fieldIndex++;
            if (fieldIndex != quotedField.length()) continue;
            int cursor = offset + fieldIndex;
            while (cursor < end && isWhitespace(frame.content().getByte(cursor))) cursor++;
            if (cursor >= end || frame.content().getByte(cursor++) != ':') continue;
            while (cursor < end && isWhitespace(frame.content().getByte(cursor))) cursor++;
            int value = 0;
            final int numberStart = cursor;
            while (cursor < end) {
                final int digit = frame.content().getByte(cursor) - '0';
                if (digit < 0 || digit > 9) break;
                value = value * 10 + digit;
                cursor++;
            }
            if (cursor > numberStart && value == expected) return true;
        }
        return false;
    }

    private static boolean isWhitespace(final byte value) {
        return value == ' ' || value == '\n' || value == '\r' || value == '\t';
    }
}
