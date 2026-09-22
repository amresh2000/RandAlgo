package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import io.netty.buffer.ByteBuf;

/** Strict parser for legacy-style Bybit private WebSocket auth/subscription controls. */
public final class BybitPrivateControlParser {
    private final JsonByteCursor cursor = new JsonByteCursor(6, 128);
    private final BybitOrderByteBufInput input = new BybitOrderByteBufInput();
    private final ByteToken field = new ByteToken();
    private final ByteToken value = new ByteToken();

    public BybitOrderParseStatus parse(
            final ByteBuf frame, final MutableBybitPrivateControl destination) {
        if (frame == null || destination == null)
            throw new NullPointerException("arguments are required");
        input.wrap(frame);
        cursor.reset(input, frame.readerIndex(), frame.readableBytes());
        if (!cursor.consume((byte) '{')) return BybitOrderParseStatus.MALFORMED;
        boolean opSeen = false;
        boolean successSeen = false;
        boolean success = false;
        BybitPrivateControlKind successfulKind = null;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return BybitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "op")) {
                if (opSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (!cursor.readAsciiString(value)) return BybitOrderParseStatus.MALFORMED;
                if (cursor.tokenEquals(value, "auth"))
                    successfulKind = BybitPrivateControlKind.AUTHENTICATED;
                else if (cursor.tokenEquals(value, "subscribe"))
                    successfulKind = BybitPrivateControlKind.SUBSCRIBED;
                else if (cursor.tokenEquals(value, "pong"))
                    successfulKind = BybitPrivateControlKind.PONG;
                else return BybitOrderParseStatus.UNSUPPORTED_MESSAGE;
                opSeen = true;
            } else if (cursor.tokenEquals(field, "success")) {
                if (successSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (cursor.peek() == 't') {
                    if (!cursor.consumeLiteral("true")) return BybitOrderParseStatus.MALFORMED;
                    success = true;
                } else if (cursor.peek() == 'f') {
                    if (!cursor.consumeLiteral("false")) return BybitOrderParseStatus.MALFORMED;
                } else return BybitOrderParseStatus.MALFORMED;
                successSeen = true;
            } else if (!cursor.skipValue()) return BybitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return BybitOrderParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd() || !opSeen || !successSeen)
            return BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
        if (success) destination.kind(successfulKind);
        else if (successfulKind == BybitPrivateControlKind.AUTHENTICATED)
            destination.kind(BybitPrivateControlKind.AUTHENTICATION_FAILED);
        else if (successfulKind == BybitPrivateControlKind.SUBSCRIBED)
            destination.kind(BybitPrivateControlKind.SUBSCRIPTION_FAILED);
        else return BybitOrderParseStatus.VENUE_REJECTED;
        return BybitOrderParseStatus.OK;
    }
}
