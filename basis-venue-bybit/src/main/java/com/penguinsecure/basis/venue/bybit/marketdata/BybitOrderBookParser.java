package com.penguinsecure.basis.venue.bybit.marketdata;

import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import com.penguinsecure.basis.venue.api.json.MutableJsonLong;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataParseStatus;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import io.netty.buffer.ByteBuf;

/** Allocation-free parser for approved Bybit V5 bounded order-book frames. */
public final class BybitOrderBookParser {
    private static final int ROOT_TOPIC = 1;
    private static final int ROOT_TYPE = 1 << 1;
    private static final int ROOT_TS = 1 << 2;
    private static final int ROOT_DATA = 1 << 3;
    private static final int ROOT_CTS = 1 << 4;
    private static final int ROOT_REQUIRED =
            ROOT_TOPIC | ROOT_TYPE | ROOT_TS | ROOT_DATA | ROOT_CTS;
    private static final int DATA_SYMBOL = 1;
    private static final int DATA_BIDS = 1 << 1;
    private static final int DATA_ASKS = 1 << 2;
    private static final int DATA_UPDATE = 1 << 3;
    private static final int DATA_SEQUENCE = 1 << 4;
    private static final int DATA_REQUIRED =
            DATA_SYMBOL | DATA_BIDS | DATA_ASKS | DATA_UPDATE | DATA_SEQUENCE;

    private final JsonByteCursor cursor = new JsonByteCursor(12, 128);
    private final ByteToken field = new ByteToken();
    private final ByteToken value = new ByteToken();
    private final MutableJsonLong number = new MutableJsonLong();
    private final BybitByteBufInput input = new BybitByteBufInput();
    private final MonotonicClock monotonicClock;

    public BybitOrderBookParser(final MonotonicClock monotonicClock) {
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
        if (frame.readableBytes() > profile.maximumFrameBytes())
            return MarketDataParseStatus.TOKEN_TOO_LONG;
        target.reset();
        target.venueId(profile.venueId());
        target.instrumentId(profile.instrumentId());
        target.feedProfileId(profile.profileId());
        target.sessionGeneration(sessionGeneration);
        target.receiveEpochNanos(receiveEpochNanos);
        target.receiveMonoNanos(receiveMonoNanos);
        input.wrap(frame);
        cursor.reset(input, frame.readerIndex(), frame.readableBytes());
        if (!cursor.consume((byte) '{')) return cursor.status();
        int seen = 0;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return cursor.status();
            final int bit = rootFieldBit(field);
            if (bit != 0 && (seen & bit) != 0)
                return MarketDataParseStatus.DUPLICATE_REQUIRED_FIELD;
            seen |= bit;
            if (bit == ROOT_TOPIC) {
                if (!cursor.readAsciiString(value)) return cursor.status();
                if (!cursor.tokenEquals(value, profile.venueChannel()))
                    return MarketDataParseStatus.UNSUPPORTED_MESSAGE;
            } else if (bit == ROOT_TYPE) {
                if (!cursor.readAsciiString(value)) return cursor.status();
                if (cursor.tokenEquals(value, "snapshot"))
                    target.kind(MarketDataEventKind.SNAPSHOT);
                else if (cursor.tokenEquals(value, "delta")) target.kind(MarketDataEventKind.DELTA);
                else return MarketDataParseStatus.UNSUPPORTED_MESSAGE;
            } else if (bit == ROOT_TS) {
                if (!cursor.readPositiveLong(number)) return cursor.status();
                target.venueTimestampMillis(number.value());
            } else if (bit == ROOT_CTS) {
                if (!cursor.readPositiveLong(number)) return cursor.status();
                target.matchingEngineTimestampMillis(number.value());
            } else if (bit == ROOT_DATA) {
                final MarketDataParseStatus dataStatus = parseData(profile, target);
                if (dataStatus != MarketDataParseStatus.OK) return dataStatus;
            } else if (!cursor.skipValue()) return cursor.status();
            if (cursor.peek() == ',') {
                cursor.consume((byte) ',');
                continue;
            }
            if (cursor.peek() != '}') return MarketDataParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd()) return MarketDataParseStatus.MALFORMED;
        if ((seen & ROOT_REQUIRED) != ROOT_REQUIRED)
            return MarketDataParseStatus.MISSING_REQUIRED_FIELD;
        target.decodeCompleteMonoNanos(monotonicClock.nanoTime());
        return MarketDataParseStatus.OK;
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
            if (bit == DATA_SYMBOL) {
                if (!cursor.readAsciiString(value)) return cursor.status();
                if (!cursor.tokenEquals(value, profile.venueInstrument()))
                    return MarketDataParseStatus.UNSUPPORTED_MESSAGE;
            } else if (bit == DATA_BIDS) {
                final MarketDataParseStatus status = parseLevels(true, profile, target);
                if (status != MarketDataParseStatus.OK) return status;
            } else if (bit == DATA_ASKS) {
                final MarketDataParseStatus status = parseLevels(false, profile, target);
                if (status != MarketDataParseStatus.OK) return status;
            } else if (bit == DATA_UPDATE) {
                if (!cursor.readPositiveLong(number)) return cursor.status();
                target.venueUpdateId(number.value());
            } else if (bit == DATA_SEQUENCE) {
                if (!cursor.readPositiveLong(number)) return cursor.status();
                target.venueSequence(number.value());
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
            if (!cursor.readScaledDecimalString(profile.priceScale(), number))
                return cursor.status();
            final long price = number.value();
            if (!cursor.consume((byte) ',')) return cursor.status();
            if (!cursor.readScaledDecimalString(profile.quantityScale(), number))
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

    private int rootFieldBit(final ByteToken token) {
        if (cursor.tokenEquals(token, "topic")) return ROOT_TOPIC;
        if (cursor.tokenEquals(token, "type")) return ROOT_TYPE;
        if (cursor.tokenEquals(token, "ts")) return ROOT_TS;
        if (cursor.tokenEquals(token, "data")) return ROOT_DATA;
        if (cursor.tokenEquals(token, "cts")) return ROOT_CTS;
        return 0;
    }

    private int dataFieldBit(final ByteToken token) {
        if (cursor.tokenEquals(token, "s")) return DATA_SYMBOL;
        if (cursor.tokenEquals(token, "b")) return DATA_BIDS;
        if (cursor.tokenEquals(token, "a")) return DATA_ASKS;
        if (cursor.tokenEquals(token, "u")) return DATA_UPDATE;
        if (cursor.tokenEquals(token, "seq")) return DATA_SEQUENCE;
        return 0;
    }
}
