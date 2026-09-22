package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import com.penguinsecure.basis.venue.api.json.MutableJsonLong;
import io.netty.buffer.ByteBuf;

/** Bounded parser for Bybit V5 trade authentication and command responses. */
public final class BybitTradeResponseParser {
    private static final int RET_CODE = 1;
    private static final int OP = 1 << 1;
    private final JsonByteCursor cursor = new JsonByteCursor(10, 128);
    private final BybitOrderByteBufInput input = new BybitOrderByteBufInput();
    private final ByteToken field = new ByteToken();
    private final ByteToken value = new ByteToken();
    private final MutableJsonLong number = new MutableJsonLong();
    private final byte[] identity = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
    private final MutableLocalOrderId localId = new MutableLocalOrderId();
    private final long[] rates = new long[3];

    public BybitOrderParseStatus parse(
            final ByteBuf frame, final MutableBybitTradeResponse target) {
        if (frame == null || target == null)
            throw new NullPointerException("arguments are required");
        input.wrap(frame);
        cursor.reset(input, frame.readerIndex(), frame.readableBytes());
        if (!cursor.consume((byte) '{')) return BybitOrderParseStatus.MALFORMED;
        int seen = 0;
        int retCode = Integer.MIN_VALUE;
        boolean auth = false;
        boolean pong = false;
        boolean hasIdentity = false;
        long limit = 0;
        long remaining = 0;
        long reset = 0;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':')) {
                return BybitOrderParseStatus.MALFORMED;
            }
            if (cursor.tokenEquals(field, "retCode")) {
                if ((seen & RET_CODE) != 0) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (!cursor.readPositiveLong(number)) return BybitOrderParseStatus.INVALID_NUMBER;
                if (number.value() > Integer.MAX_VALUE) return BybitOrderParseStatus.INVALID_NUMBER;
                retCode = (int) number.value();
                seen |= RET_CODE;
            } else if (cursor.tokenEquals(field, "op")) {
                if ((seen & OP) != 0) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (!cursor.readAsciiString(value)) return BybitOrderParseStatus.MALFORMED;
                auth = cursor.tokenEquals(value, "auth");
                pong = cursor.tokenEquals(value, "pong");
                seen |= OP;
            } else if (cursor.tokenEquals(field, "reqId")) {
                if (!cursor.readAsciiString(value)
                        || value.length() != identity.length
                        || !cursor.copyToken(value, identity, 0)
                        || !VenueClientIdEncoder.decode(identity, 0, identity.length, localId)) {
                    return BybitOrderParseStatus.INVALID_IDENTITY;
                }
                hasIdentity = true;
            } else if (cursor.tokenEquals(field, "header")) {
                rates[0] = 0;
                rates[1] = 0;
                rates[2] = 0;
                final BybitOrderParseStatus status = parseHeader(rates);
                if (status != BybitOrderParseStatus.OK) return status;
                limit = rates[0];
                remaining = rates[1];
                reset = rates[2];
            } else if (!cursor.skipValue()) {
                return BybitOrderParseStatus.MALFORMED;
            }
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return BybitOrderParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd() || (seen & OP) == 0) {
            return BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
        }
        final BybitTradeResponseKind kind;
        if (pong) kind = BybitTradeResponseKind.PONG;
        else if ((seen & RET_CODE) == 0) return BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
        else if (auth) {
            kind =
                    retCode == 0
                            ? BybitTradeResponseKind.AUTHENTICATED
                            : BybitTradeResponseKind.AUTHENTICATION_FAILED;
        } else if (!hasIdentity) {
            return BybitOrderParseStatus.INVALID_IDENTITY;
        } else if (retCode == 0) kind = BybitTradeResponseKind.COMMAND_ACCEPTED;
        else if (retCode == 10006) kind = BybitTradeResponseKind.RATE_LIMITED;
        else kind = BybitTradeResponseKind.COMMAND_REJECTED;
        target.set(
                kind,
                retCode == Integer.MIN_VALUE ? 0 : retCode,
                hasIdentity ? localId.high() : 0,
                hasIdentity ? localId.low() : 0,
                limit,
                remaining,
                reset);
        return BybitOrderParseStatus.OK;
    }

    private BybitOrderParseStatus parseHeader(final long[] rates) {
        if (!cursor.consume((byte) '{')) return BybitOrderParseStatus.MALFORMED;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':')) {
                return BybitOrderParseStatus.MALFORMED;
            }
            final int index;
            if (cursor.tokenEquals(field, "X-Bapi-Limit")) index = 0;
            else if (cursor.tokenEquals(field, "X-Bapi-Limit-Status")) index = 1;
            else if (cursor.tokenEquals(field, "X-Bapi-Limit-Reset-Timestamp")) index = 2;
            else index = -1;
            if (index >= 0) {
                if (!cursor.readScaledDecimal(0, number) || number.value() < 0) {
                    return BybitOrderParseStatus.INVALID_NUMBER;
                }
                rates[index] = number.value();
            } else if (!cursor.skipValue()) return BybitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return BybitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        return BybitOrderParseStatus.OK;
    }
}
