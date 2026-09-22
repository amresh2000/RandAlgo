package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import com.penguinsecure.basis.venue.api.json.MutableJsonLong;
import com.penguinsecure.basis.venue.api.json.ReadableBytes;
import com.penguinsecure.basis.venue.api.order.VenueOrderState;

/** Bounded parser for V5 order reconciliation pages. */
public final class BybitReconciliationResponseParser {
    private final BybitOrderProfile profile;
    private final JsonByteCursor cursor = new JsonByteCursor(12, 256);
    private final ArrayInput input = new ArrayInput();
    private final ByteToken field = new ByteToken();
    private final ByteToken value = new ByteToken();
    private final ByteToken orderLinkId = new ByteToken();
    private final ByteToken orderStatus = new ByteToken();
    private final MutableJsonLong number = new MutableJsonLong();
    private final MutableLocalOrderId localId = new MutableLocalOrderId();
    private final byte[] identity = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
    private final byte[] cursorBytes = new byte[256];

    public BybitReconciliationResponseParser(final BybitOrderProfile profile) {
        if (profile == null) throw new NullPointerException("profile is required");
        this.profile = profile;
    }

    public BybitOrderParseStatus parse(
            final byte[] response,
            final int length,
            final long expectedSessionGeneration,
            final BybitReconciliationPage destination) {
        if (response == null || destination == null)
            throw new NullPointerException("arguments are required");
        if (length < 0 || length > response.length || expectedSessionGeneration <= 0)
            return BybitOrderParseStatus.INVALID_NUMBER;
        destination.reset();
        input.wrap(response);
        cursor.reset(input, 0, length);
        if (!cursor.consume((byte) '{')) return BybitOrderParseStatus.MALFORMED;
        boolean retCodeSeen = false;
        boolean resultSeen = false;
        long retCode = -1;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return BybitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "retCode")) {
                if (retCodeSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (!cursor.readPositiveLong(number)) return BybitOrderParseStatus.INVALID_NUMBER;
                retCode = number.value();
                retCodeSeen = true;
            } else if (cursor.tokenEquals(field, "result")) {
                if (resultSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                final BybitOrderParseStatus status =
                        parseResult(expectedSessionGeneration, destination);
                if (status != BybitOrderParseStatus.OK) return status;
                resultSeen = true;
            } else if (!cursor.skipValue()) return BybitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return BybitOrderParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd()) return BybitOrderParseStatus.MALFORMED;
        if (!retCodeSeen || !resultSeen) return BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
        return retCode == 0 ? BybitOrderParseStatus.OK : BybitOrderParseStatus.VENUE_REJECTED;
    }

    private BybitOrderParseStatus parseResult(
            final long generation, final BybitReconciliationPage destination) {
        if (!cursor.consume((byte) '{')) return BybitOrderParseStatus.MALFORMED;
        boolean listSeen = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return BybitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "category")) {
                if (!cursor.readAsciiString(value)
                        || !cursor.tokenEquals(value, profile.category()))
                    return BybitOrderParseStatus.UNSUPPORTED_MESSAGE;
            } else if (cursor.tokenEquals(field, "nextPageCursor")) {
                if (!cursor.readAsciiString(value)
                        || value.length() > cursorBytes.length
                        || !cursor.copyToken(value, cursorBytes, 0)
                        || !destination.nextCursor(cursorBytes, 0, value.length()))
                    return BybitOrderParseStatus.CAPACITY_EXHAUSTED;
            } else if (cursor.tokenEquals(field, "list")) {
                if (listSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                final BybitOrderParseStatus status = parseList(generation, destination);
                if (status != BybitOrderParseStatus.OK) return status;
                listSeen = true;
            } else if (!cursor.skipValue()) return BybitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return BybitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        return listSeen ? BybitOrderParseStatus.OK : BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
    }

    private BybitOrderParseStatus parseList(
            final long generation, final BybitReconciliationPage destination) {
        if (!cursor.consume((byte) '[')) return BybitOrderParseStatus.MALFORMED;
        while (cursor.peek() != ']') {
            final BybitOrderParseStatus status = parseOrder(generation, destination);
            if (status != BybitOrderParseStatus.OK) return status;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != ']') return BybitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) ']');
        return BybitOrderParseStatus.OK;
    }

    private BybitOrderParseStatus parseOrder(
            final long generation, final BybitReconciliationPage destination) {
        if (!cursor.consume((byte) '{')) return BybitOrderParseStatus.MALFORMED;
        boolean idSeen = false;
        boolean statusSeen = false;
        boolean filledSeen = false;
        long filled = 0;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return BybitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "orderLinkId")) {
                if (idSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (!cursor.readAsciiString(orderLinkId)) return BybitOrderParseStatus.MALFORMED;
                idSeen = true;
            } else if (cursor.tokenEquals(field, "orderStatus")) {
                if (statusSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (!cursor.readAsciiString(orderStatus)) return BybitOrderParseStatus.MALFORMED;
                statusSeen = true;
            } else if (cursor.tokenEquals(field, "cumExecQty")) {
                if (filledSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (!cursor.readScaledDecimalString(profile.quantityScale(), number))
                    return BybitOrderParseStatus.INVALID_NUMBER;
                filled = number.value();
                filledSeen = true;
            } else if (!cursor.skipValue()) return BybitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return BybitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        if (!idSeen || !statusSeen || !filledSeen)
            return BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
        if (orderLinkId.length() != identity.length
                || !cursor.copyToken(orderLinkId, identity, 0)
                || !VenueClientIdEncoder.decode(identity, 0, identity.length, localId)
                || ((localId.high() >>> 32) & 0xffffL) != profile.venueId())
            return BybitOrderParseStatus.INVALID_IDENTITY;
        final VenueOrderState state = state();
        if (state == null) return BybitOrderParseStatus.UNSUPPORTED_MESSAGE;
        return destination.add(localId.high(), localId.low(), filled, state)
                ? BybitOrderParseStatus.OK
                : BybitOrderParseStatus.CAPACITY_EXHAUSTED;
    }

    private VenueOrderState state() {
        if (cursor.tokenEquals(orderStatus, "New") || cursor.tokenEquals(orderStatus, "Created"))
            return VenueOrderState.WORKING;
        if (cursor.tokenEquals(orderStatus, "PartiallyFilled"))
            return VenueOrderState.PARTIALLY_FILLED;
        if (cursor.tokenEquals(orderStatus, "Filled")) return VenueOrderState.FILLED;
        if (cursor.tokenEquals(orderStatus, "Cancelled")
                || cursor.tokenEquals(orderStatus, "PartiallyFilledCanceled")
                || cursor.tokenEquals(orderStatus, "Deactivated")) return VenueOrderState.CANCELLED;
        if (cursor.tokenEquals(orderStatus, "Rejected")) return VenueOrderState.REJECTED;
        return null;
    }

    private static final class ArrayInput implements ReadableBytes {
        private byte[] bytes;

        private void wrap(final byte[] value) {
            bytes = value;
        }

        @Override
        public byte getByte(final int index) {
            return bytes[index];
        }
    }
}
