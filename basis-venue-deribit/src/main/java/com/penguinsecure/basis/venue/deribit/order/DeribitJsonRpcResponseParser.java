package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import com.penguinsecure.basis.venue.api.json.MutableJsonLong;
import io.netty.buffer.ByteBuf;

/** Bounded field-order-independent parser for Deribit JSON-RPC responses and heartbeats. */
public final class DeribitJsonRpcResponseParser {
    private final JsonByteCursor cursor = new JsonByteCursor(12, 4096);
    private final DeribitOrderByteBufInput input = new DeribitOrderByteBufInput();
    private final ByteToken field = new ByteToken();
    private final ByteToken value = new ByteToken();
    private final MutableJsonLong number = new MutableJsonLong();
    private boolean testRequest;

    public DeribitOrderParseStatus parse(final ByteBuf frame, final MutableDeribitResponse target) {
        if (frame == null || target == null)
            throw new NullPointerException("arguments are required");
        target.reset();
        testRequest = false;
        input.wrap(frame);
        cursor.reset(input, frame.readerIndex(), frame.readableBytes());
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        boolean idSeen = false;
        boolean resultSeen = false;
        boolean errorSeen = false;
        boolean heartbeat = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "id")) {
                if (idSeen || !cursor.readPositiveLong(number) || number.value() <= 0)
                    return idSeen
                            ? DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD
                            : DeribitOrderParseStatus.INVALID_NUMBER;
                target.requestId(number.value());
                idSeen = true;
            } else if (cursor.tokenEquals(field, "result")) {
                if (resultSeen) return DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                final DeribitOrderParseStatus status = parseResult(target);
                if (status != DeribitOrderParseStatus.OK) return status;
                target.result(true);
                resultSeen = true;
            } else if (cursor.tokenEquals(field, "error")) {
                if (errorSeen) return DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                final DeribitOrderParseStatus status = parseError(target);
                if (status != DeribitOrderParseStatus.OK) return status;
                errorSeen = true;
            } else if (cursor.tokenEquals(field, "method")) {
                if (!cursor.readAsciiString(value)) return DeribitOrderParseStatus.MALFORMED;
                heartbeat = cursor.tokenEquals(value, "heartbeat");
            } else if (cursor.tokenEquals(field, "params")) {
                final DeribitOrderParseStatus status = parseHeartbeat(target);
                if (status != DeribitOrderParseStatus.OK) return status;
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd())
            return DeribitOrderParseStatus.MALFORMED;
        target.heartbeatTest(heartbeat && testRequest);
        if (target.heartbeatTest()) return DeribitOrderParseStatus.OK;
        if (!idSeen || resultSeen == errorSeen)
            return DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
        return DeribitOrderParseStatus.OK;
    }

    private DeribitOrderParseStatus parseResult(final MutableDeribitResponse target) {
        if (cursor.peek() != '{')
            return cursor.skipValue()
                    ? DeribitOrderParseStatus.OK
                    : DeribitOrderParseStatus.MALFORMED;
        cursor.consume((byte) '{');
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "access_token")) {
                if (!cursor.readAsciiString(value)
                        || value.length() > target.mutableAccessToken().length
                        || !cursor.copyToken(value, target.mutableAccessToken(), 0))
                    return DeribitOrderParseStatus.MALFORMED;
                target.accessLength(value.length());
            } else if (cursor.tokenEquals(field, "refresh_token")) {
                if (!cursor.readAsciiString(value)
                        || value.length() > target.mutableRefreshToken().length
                        || !cursor.copyToken(value, target.mutableRefreshToken(), 0))
                    return DeribitOrderParseStatus.MALFORMED;
                target.refreshLength(value.length());
            } else if (cursor.tokenEquals(field, "expires_in")) {
                if (!cursor.readPositiveLong(number)) return DeribitOrderParseStatus.INVALID_NUMBER;
                target.expiresInSeconds(number.value());
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        return DeribitOrderParseStatus.OK;
    }

    private DeribitOrderParseStatus parseError(final MutableDeribitResponse target) {
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        boolean code = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "code")) {
                if (code
                        || !cursor.readScaledDecimal(0, number)
                        || number.value() < Integer.MIN_VALUE
                        || number.value() > Integer.MAX_VALUE)
                    return code
                            ? DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD
                            : DeribitOrderParseStatus.INVALID_NUMBER;
                target.errorCode((int) number.value());
                if (number.value() == 0) return DeribitOrderParseStatus.INVALID_NUMBER;
                code = true;
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        return code ? DeribitOrderParseStatus.OK : DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
    }

    private DeribitOrderParseStatus parseHeartbeat(final MutableDeribitResponse target) {
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "type")) {
                if (!cursor.readAsciiString(value)) return DeribitOrderParseStatus.MALFORMED;
                testRequest = cursor.tokenEquals(value, "test_request");
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        return DeribitOrderParseStatus.OK;
    }
}
