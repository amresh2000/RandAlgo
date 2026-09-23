package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import com.penguinsecure.basis.venue.api.json.MutableJsonLong;
import com.penguinsecure.basis.venue.api.json.ReadableBytes;
import com.penguinsecure.basis.venue.api.order.VenueOrderState;

/** Bounded parser for Deribit open-order arrays and history entry pages. */
public final class DeribitReconciliationResponseParser {
    private final DeribitOrderProfile profile;
    private final JsonByteCursor cursor = new JsonByteCursor(14, 4096);
    private final ArrayInput input = new ArrayInput();
    private final ByteToken field = new ByteToken(),
            label = new ByteToken(),
            state = new ByteToken(),
            instrument = new ByteToken();
    private final MutableJsonLong number = new MutableJsonLong();
    private final MutableLocalOrderId localId = new MutableLocalOrderId();
    private final byte[] identity = new byte[VenueClientIdEncoder.ENCODED_LENGTH];

    public DeribitReconciliationResponseParser(final DeribitOrderProfile profile) {
        if (profile == null) throw new NullPointerException("profile is required");
        this.profile = profile;
    }

    public DeribitOrderParseStatus parse(
            final byte[] response, final int length, final DeribitReconciliationPage page) {
        if (response == null || page == null)
            throw new NullPointerException("arguments are required");
        if (length < 0 || length > response.length) return DeribitOrderParseStatus.INVALID_NUMBER;
        page.reset();
        input.wrap(response);
        cursor.reset(input, 0, length);
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        boolean result = false, error = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "result")) {
                if (result) return DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                final DeribitOrderParseStatus status = parseResult(page);
                if (status != DeribitOrderParseStatus.OK) return status;
                result = true;
            } else if (cursor.tokenEquals(field, "error")) {
                if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
                error = true;
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd())
            return DeribitOrderParseStatus.MALFORMED;
        return error
                ? DeribitOrderParseStatus.VENUE_REJECTED
                : result
                        ? DeribitOrderParseStatus.OK
                        : DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
    }

    private DeribitOrderParseStatus parseResult(final DeribitReconciliationPage page) {
        if (cursor.peek() == '[') return parseList(page);
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        boolean entries = false;
        int continuation = 0;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "entries")) {
                if (entries) return DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                final DeribitOrderParseStatus status = parseList(page);
                if (status != DeribitOrderParseStatus.OK) return status;
                entries = true;
            } else if (cursor.tokenEquals(field, "continuation")) {
                if (cursor.peek() == 'n') {
                    if (!cursor.consumeLiteral("null")) return DeribitOrderParseStatus.MALFORMED;
                } else {
                    if (!cursor.readPositiveLong(number) || number.value() > Integer.MAX_VALUE)
                        return DeribitOrderParseStatus.INVALID_NUMBER;
                    continuation = (int) number.value();
                }
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        page.continuation(continuation, continuation > 0);
        return entries
                ? DeribitOrderParseStatus.OK
                : DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
    }

    private DeribitOrderParseStatus parseList(final DeribitReconciliationPage page) {
        if (!cursor.consume((byte) '[')) return DeribitOrderParseStatus.MALFORMED;
        while (cursor.peek() != ']') {
            final DeribitOrderParseStatus status = parseOrder(page);
            if (status != DeribitOrderParseStatus.OK) return status;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != ']') return DeribitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) ']');
        return DeribitOrderParseStatus.OK;
    }

    private DeribitOrderParseStatus parseOrder(final DeribitReconciliationPage page) {
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        boolean hasLabel = false, hasState = false, hasFilled = false, hasInstrument = false;
        long filled = 0;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "label")) {
                if (hasLabel || !cursor.readAsciiString(label)) return duplicate(hasLabel);
                hasLabel = true;
            } else if (cursor.tokenEquals(field, "order_state")) {
                if (hasState || !cursor.readAsciiString(state)) return duplicate(hasState);
                hasState = true;
            } else if (cursor.tokenEquals(field, "filled_amount")) {
                if (hasFilled
                        || !cursor.readScaledDecimalWithExponent(profile.amountScale(), number))
                    return duplicate(hasFilled);
                filled = number.value();
                hasFilled = true;
            } else if (cursor.tokenEquals(field, "instrument_name")) {
                if (hasInstrument || !cursor.readAsciiString(instrument))
                    return duplicate(hasInstrument);
                hasInstrument = true;
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        if (!hasLabel || !hasState || !hasFilled || !hasInstrument)
            return DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
        if (!cursor.tokenEquals(instrument, profile.instrument()))
            return DeribitOrderParseStatus.UNSUPPORTED_MESSAGE;
        if (label.length() != identity.length
                || !cursor.copyToken(label, identity, 0)
                || !VenueClientIdEncoder.decode(identity, 0, identity.length, localId)
                || ((localId.high() >>> 32) & 0xffffL) != profile.venueId())
            return DeribitOrderParseStatus.INVALID_IDENTITY;
        final VenueOrderState mapped = mapState();
        if (mapped == null) return DeribitOrderParseStatus.UNSUPPORTED_MESSAGE;
        return page.add(localId.high(), localId.low(), filled, mapped)
                ? DeribitOrderParseStatus.OK
                : DeribitOrderParseStatus.CAPACITY_EXHAUSTED;
    }

    private VenueOrderState mapState() {
        if (cursor.tokenEquals(state, "open") || cursor.tokenEquals(state, "untriggered"))
            return VenueOrderState.WORKING;
        if (cursor.tokenEquals(state, "filled")) return VenueOrderState.FILLED;
        if (cursor.tokenEquals(state, "cancelled")) return VenueOrderState.CANCELLED;
        if (cursor.tokenEquals(state, "rejected")) return VenueOrderState.REJECTED;
        return null;
    }

    private static DeribitOrderParseStatus duplicate(final boolean duplicate) {
        return duplicate
                ? DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD
                : DeribitOrderParseStatus.MALFORMED;
    }

    private static final class ArrayInput implements ReadableBytes {
        private byte[] bytes;

        void wrap(final byte[] value) {
            bytes = value;
        }

        public byte getByte(final int index) {
            return bytes[index];
        }
    }
}
