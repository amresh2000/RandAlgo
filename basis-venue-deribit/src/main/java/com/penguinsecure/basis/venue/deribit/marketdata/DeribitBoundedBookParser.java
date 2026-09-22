package com.penguinsecure.basis.venue.deribit.marketdata;

import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import com.penguinsecure.basis.venue.api.json.MutableJsonLong;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataParseStatus;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import io.netty.buffer.ByteBuf;

/** Parser for capture-certified `book.<instrument>.none.<depth>.<interval>` images. */
public final class DeribitBoundedBookParser {
    private static final int DATA_TIMESTAMP = 1;
    private static final int DATA_INSTRUMENT = 1 << 1;
    private static final int DATA_CHANGE = 1 << 2;
    private static final int DATA_BIDS = 1 << 3;
    private static final int DATA_ASKS = 1 << 4;
    private static final int DATA_REQUIRED =
            DATA_TIMESTAMP | DATA_INSTRUMENT | DATA_CHANGE | DATA_BIDS | DATA_ASKS;

    private final JsonByteCursor cursor = new JsonByteCursor(12, 160);
    private final ByteToken field = new ByteToken();
    private final ByteToken value = new ByteToken();
    private final MutableJsonLong number = new MutableJsonLong();
    private final DeribitByteBufInput input = new DeribitByteBufInput();
    private final MonotonicClock monotonicClock;

    public DeribitBoundedBookParser(final MonotonicClock monotonicClock) {
        if (monotonicClock == null) throw new NullPointerException("monotonicClock is required");
        this.monotonicClock = monotonicClock;
    }

    public MarketDataParseStatus parse(
            final ByteBuf frame,
            final MarketDataFeedProfile profile,
            final long sessionGeneration,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final MutableMarketDataEvent target) {
        if (frame == null || profile == null || target == null)
            throw new NullPointerException("arguments are required");
        if (!profile.completeImageCertified()) return MarketDataParseStatus.UNSUPPORTED_PROFILE;
        if (frame.readableBytes() > profile.maximumFrameBytes())
            return MarketDataParseStatus.TOKEN_TOO_LONG;
        target.reset();
        target.venueId(profile.venueId());
        target.instrumentId(profile.instrumentId());
        target.feedProfileId(profile.profileId());
        target.sessionGeneration(sessionGeneration);
        target.receiveEpochNanos(receiveEpochNanos);
        target.receiveMonoNanos(receiveMonoNanos);
        target.kind(MarketDataEventKind.IMAGE);
        input.wrap(frame);
        cursor.reset(input, frame.readerIndex(), frame.readableBytes());
        if (!cursor.consume((byte) '{')) return cursor.status();
        boolean parsedData = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return cursor.status();
            if (cursor.tokenEquals(field, "data")) {
                if (parsedData) return MarketDataParseStatus.DUPLICATE_REQUIRED_FIELD;
                final MarketDataParseStatus status = parseData(profile, target);
                if (status != MarketDataParseStatus.OK) return status;
                parsedData = true;
            } else if (cursor.tokenEquals(field, "params")) {
                if (parsedData) return MarketDataParseStatus.DUPLICATE_REQUIRED_FIELD;
                final MarketDataParseStatus status = parseParams(profile, target);
                if (status != MarketDataParseStatus.OK) return status;
                parsedData = true;
            } else if (!cursor.skipValue()) return cursor.status();
            if (cursor.peek() == ',') {
                cursor.consume((byte) ',');
                continue;
            }
            if (cursor.peek() != '}') return MarketDataParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd()) return MarketDataParseStatus.MALFORMED;
        if (!parsedData) return MarketDataParseStatus.MISSING_REQUIRED_FIELD;
        target.decodeCompleteMonoNanos(monotonicClock.nanoTime());
        return MarketDataParseStatus.OK;
    }

    private MarketDataParseStatus parseParams(
            final MarketDataFeedProfile profile, final MutableMarketDataEvent target) {
        if (!cursor.consume((byte) '{')) return cursor.status();
        boolean channel = false;
        boolean data = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return cursor.status();
            if (cursor.tokenEquals(field, "channel")) {
                if (channel) return MarketDataParseStatus.DUPLICATE_REQUIRED_FIELD;
                if (!cursor.readAsciiString(value)) return cursor.status();
                if (!cursor.tokenEquals(value, profile.venueChannel()))
                    return MarketDataParseStatus.UNSUPPORTED_MESSAGE;
                channel = true;
            } else if (cursor.tokenEquals(field, "data")) {
                if (data) return MarketDataParseStatus.DUPLICATE_REQUIRED_FIELD;
                final MarketDataParseStatus status = parseData(profile, target);
                if (status != MarketDataParseStatus.OK) return status;
                data = true;
            } else if (!cursor.skipValue()) return cursor.status();
            if (cursor.peek() == ',') {
                cursor.consume((byte) ',');
                continue;
            }
            if (cursor.peek() != '}') return MarketDataParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        return channel && data
                ? MarketDataParseStatus.OK
                : MarketDataParseStatus.MISSING_REQUIRED_FIELD;
    }

    private MarketDataParseStatus parseData(
            final MarketDataFeedProfile profile, final MutableMarketDataEvent target) {
        if (!cursor.consume((byte) '{')) return cursor.status();
        int seen = 0;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return cursor.status();
            final int bit = dataFieldBit(field);
            if (bit != 0 && (seen & bit) != 0)
                return MarketDataParseStatus.DUPLICATE_REQUIRED_FIELD;
            seen |= bit;
            if (bit == DATA_TIMESTAMP) {
                if (!cursor.readPositiveLong(number)) return cursor.status();
                target.venueTimestampMillis(number.value());
            } else if (bit == DATA_INSTRUMENT) {
                if (!cursor.readAsciiString(value)) return cursor.status();
                if (!cursor.tokenEquals(value, profile.venueInstrument()))
                    return MarketDataParseStatus.UNSUPPORTED_MESSAGE;
            } else if (bit == DATA_CHANGE) {
                if (!cursor.readPositiveLong(number)) return cursor.status();
                target.venueChangeId(number.value());
            } else if (bit == DATA_BIDS) {
                final MarketDataParseStatus status = parseLevels(true, profile, target);
                if (status != MarketDataParseStatus.OK) return status;
            } else if (bit == DATA_ASKS) {
                final MarketDataParseStatus status = parseLevels(false, profile, target);
                if (status != MarketDataParseStatus.OK) return status;
            } else if (!cursor.skipValue()) return cursor.status();
            if (cursor.peek() == ',') {
                cursor.consume((byte) ',');
                continue;
            }
            if (cursor.peek() != '}') return MarketDataParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        return (seen & DATA_REQUIRED) == DATA_REQUIRED
                ? MarketDataParseStatus.OK
                : MarketDataParseStatus.MISSING_REQUIRED_FIELD;
    }

    private MarketDataParseStatus parseLevels(
            final boolean bids,
            final MarketDataFeedProfile profile,
            final MutableMarketDataEvent target) {
        if (!cursor.consume((byte) '[')) return cursor.status();
        if (cursor.peek() == ']') {
            cursor.consume((byte) ']');
            return MarketDataParseStatus.OK;
        }
        int count = 0;
        while (true) {
            if (++count > profile.maximumDepth()) return MarketDataParseStatus.TOO_MANY_LEVELS;
            if (!cursor.consume((byte) '[')) return cursor.status();
            if (!cursor.readScaledDecimalWithExponent(profile.priceScale(), number))
                return cursor.status();
            final long price = number.value();
            if (!cursor.consume((byte) ',')) return cursor.status();
            if (!cursor.readScaledDecimalWithExponent(profile.quantityScale(), number))
                return cursor.status();
            final long quantity = number.value();
            if (!cursor.consume((byte) ']')) return cursor.status();
            if (price <= 0 || quantity < 0) return MarketDataParseStatus.INVALID_NUMBER;
            if (!(bids ? target.addBid(price, quantity) : target.addAsk(price, quantity))) {
                return MarketDataParseStatus.TOO_MANY_LEVELS;
            }
            if (cursor.peek() == ',') {
                cursor.consume((byte) ',');
                continue;
            }
            if (!cursor.consume((byte) ']')) return cursor.status();
            return MarketDataParseStatus.OK;
        }
    }

    private int dataFieldBit(final ByteToken token) {
        if (cursor.tokenEquals(token, "timestamp")) return DATA_TIMESTAMP;
        if (cursor.tokenEquals(token, "instrument_name")) return DATA_INSTRUMENT;
        if (cursor.tokenEquals(token, "change_id")) return DATA_CHANGE;
        if (cursor.tokenEquals(token, "bids")) return DATA_BIDS;
        if (cursor.tokenEquals(token, "asks")) return DATA_ASKS;
        return 0;
    }
}
