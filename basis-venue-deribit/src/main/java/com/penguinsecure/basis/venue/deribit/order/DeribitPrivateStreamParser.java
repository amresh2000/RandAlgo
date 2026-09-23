package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import com.penguinsecure.basis.venue.api.json.MutableJsonLong;
import com.penguinsecure.basis.venue.api.order.MutableVenueOrderFact;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactSink;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactType;
import io.netty.buffer.ByteBuf;

/** Bounded normalizer for raw Deribit user order and trade subscriptions. */
public final class DeribitPrivateStreamParser {
    private enum Topic {
        ORDER,
        TRADE,
        IGNORED,
        UNSUPPORTED
    }

    private final DeribitOrderProfile profile;
    private final DeribitActiveOrderTable activeOrders;
    private final DeribitRequestCorrelationTable correlations;
    private final DeribitTradeDeduplicator trades;
    private final JsonByteCursor cursor = new JsonByteCursor(14, 4096);
    private final DeribitOrderByteBufInput input = new DeribitOrderByteBufInput();
    private final ByteToken field = new ByteToken(),
            value = new ByteToken(),
            channel = new ByteToken();
    private final ByteToken label = new ByteToken(),
            orderId = new ByteToken(),
            instrument = new ByteToken();
    private final ByteToken state = new ByteToken(), tradeId = new ByteToken();
    private final MutableJsonLong number = new MutableJsonLong();
    private final MutableLocalOrderId localId = new MutableLocalOrderId();
    private final byte[] identity = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
    private final MutableVenueOrderFact fact = new MutableVenueOrderFact();
    private int factsPublished, duplicates;

    public DeribitPrivateStreamParser(
            final DeribitOrderProfile profile,
            final DeribitActiveOrderTable activeOrders,
            final DeribitRequestCorrelationTable correlations,
            final int tradeCapacity) {
        if (profile == null || activeOrders == null || correlations == null)
            throw new NullPointerException("dependencies are required");
        this.profile = profile;
        this.activeOrders = activeOrders;
        this.correlations = correlations;
        trades = new DeribitTradeDeduplicator(tradeCapacity);
    }

    public DeribitOrderParseStatus parse(
            final ByteBuf frame,
            final long sessionGeneration,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final VenueOrderFactSink sink) {
        if (frame == null || sink == null) throw new NullPointerException("arguments are required");
        if (sessionGeneration <= 0 || receiveEpochNanos <= 0 || receiveMonoNanos <= 0)
            return DeribitOrderParseStatus.INVALID_NUMBER;
        input.wrap(frame);
        final Topic topic = detectTopic(frame);
        if (topic == Topic.IGNORED) return DeribitOrderParseStatus.IGNORED;
        if (topic == Topic.UNSUPPORTED) return DeribitOrderParseStatus.UNSUPPORTED_MESSAGE;
        cursor.reset(input, frame.readerIndex(), frame.readableBytes());
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        boolean params = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "params")) {
                if (params) return DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                final DeribitOrderParseStatus status =
                        parseParams(
                                topic,
                                sessionGeneration,
                                receiveEpochNanos,
                                receiveMonoNanos,
                                sink);
                if (status != DeribitOrderParseStatus.OK) return status;
                params = true;
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd())
            return DeribitOrderParseStatus.MALFORMED;
        return params ? DeribitOrderParseStatus.OK : DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
    }

    public int factsPublished() {
        return factsPublished;
    }

    public int duplicates() {
        return duplicates;
    }

    private Topic detectTopic(final ByteBuf frame) {
        cursor.reset(input, frame.readerIndex(), frame.readableBytes());
        if (!cursor.consume((byte) '{')) return Topic.UNSUPPORTED;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return Topic.UNSUPPORTED;
            if (cursor.tokenEquals(field, "params")) {
                if (!cursor.consume((byte) '{')) return Topic.UNSUPPORTED;
                while (cursor.peek() != '}') {
                    if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                        return Topic.UNSUPPORTED;
                    if (cursor.tokenEquals(field, "channel")) {
                        if (!cursor.readAsciiString(channel)) return Topic.UNSUPPORTED;
                        if (cursor.tokenStartsWith(channel, "user.orders.")) return Topic.ORDER;
                        if (cursor.tokenStartsWith(channel, "user.trades.")) return Topic.TRADE;
                        if (cursor.tokenStartsWith(channel, "user.portfolio."))
                            return Topic.IGNORED;
                        return Topic.UNSUPPORTED;
                    }
                    if (!cursor.skipValue()) return Topic.UNSUPPORTED;
                    if (cursor.peek() == ',') cursor.consume((byte) ',');
                    else if (cursor.peek() != '}') return Topic.UNSUPPORTED;
                }
                return Topic.UNSUPPORTED;
            }
            if (!cursor.skipValue()) return Topic.UNSUPPORTED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return Topic.UNSUPPORTED;
        }
        return Topic.UNSUPPORTED;
    }

    private DeribitOrderParseStatus parseParams(
            final Topic topic,
            final long generation,
            final long epoch,
            final long mono,
            final VenueOrderFactSink sink) {
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        boolean data = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "data")) {
                if (data) return DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                final DeribitOrderParseStatus status =
                        parseData(topic, generation, epoch, mono, sink);
                if (status != DeribitOrderParseStatus.OK) return status;
                data = true;
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        return data ? DeribitOrderParseStatus.OK : DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
    }

    private DeribitOrderParseStatus parseData(
            final Topic topic,
            final long generation,
            final long epoch,
            final long mono,
            final VenueOrderFactSink sink) {
        final boolean array = cursor.peek() == '[';
        if (array) cursor.consume((byte) '[');
        do {
            final DeribitOrderParseStatus status =
                    parseObject(topic, generation, epoch, mono, sink);
            if (status != DeribitOrderParseStatus.OK) return status;
            if (!array) return DeribitOrderParseStatus.OK;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != ']') return DeribitOrderParseStatus.MALFORMED;
        } while (cursor.peek() != ']');
        cursor.consume((byte) ']');
        return DeribitOrderParseStatus.OK;
    }

    private DeribitOrderParseStatus parseObject(
            final Topic topic,
            final long generation,
            final long epoch,
            final long mono,
            final VenueOrderFactSink sink) {
        if (!cursor.consume((byte) '{')) return DeribitOrderParseStatus.MALFORMED;
        boolean hasLabel = false, hasOrderId = false, hasInstrument = false, hasState = false;
        boolean hasTradeId = false, hasAmount = false, hasPrice = false;
        long amount = 0, price = 0;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':'))
                return DeribitOrderParseStatus.MALFORMED;
            if (cursor.tokenEquals(field, "label")) {
                if (hasLabel || !cursor.readAsciiString(label)) return duplicate(hasLabel);
                hasLabel = true;
            } else if (cursor.tokenEquals(field, "order_id")) {
                if (hasOrderId || !cursor.readAsciiString(orderId)) return duplicate(hasOrderId);
                hasOrderId = true;
            } else if (cursor.tokenEquals(field, "instrument_name")) {
                if (hasInstrument || !cursor.readAsciiString(instrument))
                    return duplicate(hasInstrument);
                hasInstrument = true;
            } else if (cursor.tokenEquals(field, "order_state")) {
                if (hasState || !cursor.readAsciiString(state)) return duplicate(hasState);
                hasState = true;
            } else if (cursor.tokenEquals(field, "trade_id")) {
                if (hasTradeId || !cursor.readAsciiString(tradeId)) return duplicate(hasTradeId);
                hasTradeId = true;
            } else if (cursor.tokenEquals(field, "amount")) {
                if (hasAmount
                        || !cursor.readScaledDecimalWithExponent(profile.amountScale(), number))
                    return duplicate(hasAmount);
                amount = number.value();
                hasAmount = true;
            } else if (cursor.tokenEquals(field, "price")) {
                if (hasPrice || !cursor.readScaledDecimalWithExponent(profile.priceScale(), number))
                    return duplicate(hasPrice);
                price = number.value();
                hasPrice = true;
            } else if (!cursor.skipValue()) return DeribitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return DeribitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        if (!hasInstrument || !cursor.tokenEquals(instrument, profile.instrument()) || !hasOrderId)
            return hasInstrument
                    ? DeribitOrderParseStatus.UNSUPPORTED_MESSAGE
                    : DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
        int activeIndex = -1;
        if (hasLabel) {
            if (!decodeLabel(generation)) return DeribitOrderParseStatus.IGNORED;
            activeIndex = activeOrders.find(localId.high(), localId.low());
        }
        if (activeIndex < 0) activeIndex = activeOrders.findVenueId(cursor, orderId);
        if (activeIndex < 0) return DeribitOrderParseStatus.IGNORED;
        if (!activeOrders.bindVenueId(activeIndex, cursor, orderId))
            return DeribitOrderParseStatus.CONFLICT;
        final long high = activeOrders.idHigh(activeIndex), low = activeOrders.idLow(activeIndex);
        correlations.remove(correlations.findIdentity(high, low, generation));
        if (topic == Topic.ORDER) {
            if (!hasState) return DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
            final VenueOrderFactType type = orderType();
            if (type == null) {
                if (cursor.tokenEquals(state, "filled")) activeOrders.remove(activeIndex);
                return DeribitOrderParseStatus.OK;
            }
            final DeribitOrderParseStatus status =
                    publish(type, high, low, 0, 0, 0, generation, epoch, mono, sink);
            if (status == DeribitOrderParseStatus.OK
                    && (type == VenueOrderFactType.CANCELLED
                            || type == VenueOrderFactType.REJECTED))
                activeOrders.remove(activeIndex);
            return status;
        }
        if (!hasTradeId || !hasAmount || !hasPrice || amount <= 0 || price <= 0)
            return DeribitOrderParseStatus.MISSING_REQUIRED_FIELD;
        final DeribitTradeDeduplicator.Result admitted =
                trades.admit(cursor, tradeId, high, low, amount, price);
        if (admitted == DeribitTradeDeduplicator.Result.DUPLICATE) {
            duplicates++;
            return DeribitOrderParseStatus.OK;
        }
        if (admitted == DeribitTradeDeduplicator.Result.CONFLICT)
            return DeribitOrderParseStatus.CONFLICT;
        if (admitted == DeribitTradeDeduplicator.Result.CAPACITY_EXHAUSTED)
            return DeribitOrderParseStatus.CAPACITY_EXHAUSTED;
        if (!activeOrders.addFill(activeIndex, amount)) return DeribitOrderParseStatus.CONFLICT;
        return publish(
                VenueOrderFactType.FILL,
                high,
                low,
                cursor.tokenHash64(tradeId),
                amount,
                price,
                generation,
                epoch,
                mono,
                sink);
    }

    private boolean decodeLabel(final long generation) {
        return label.length() == identity.length
                && cursor.copyToken(label, identity, 0)
                && VenueClientIdEncoder.decode(identity, 0, identity.length, localId)
                && (localId.high() & 0xffff_ffffL) == generation
                && ((localId.high() >>> 32) & 0xffffL) == profile.venueId();
    }

    private VenueOrderFactType orderType() {
        if (cursor.tokenEquals(state, "open") || cursor.tokenEquals(state, "untriggered"))
            return VenueOrderFactType.ACKNOWLEDGED;
        if (cursor.tokenEquals(state, "cancelled")) return VenueOrderFactType.CANCELLED;
        if (cursor.tokenEquals(state, "rejected")) return VenueOrderFactType.REJECTED;
        return null;
    }

    private DeribitOrderParseStatus publish(
            final VenueOrderFactType type,
            final long high,
            final long low,
            final long executionHash,
            final long amount,
            final long price,
            final long generation,
            final long epoch,
            final long mono,
            final VenueOrderFactSink sink) {
        fact.set(
                type,
                high,
                low,
                profile.venueId(),
                profile.instrumentId(),
                generation,
                epoch,
                mono,
                executionHash,
                amount,
                price,
                0,
                null,
                0);
        if (!sink.publish(fact)) return DeribitOrderParseStatus.CAPACITY_EXHAUSTED;
        factsPublished++;
        return DeribitOrderParseStatus.OK;
    }

    private static DeribitOrderParseStatus duplicate(final boolean duplicate) {
        return duplicate
                ? DeribitOrderParseStatus.DUPLICATE_REQUIRED_FIELD
                : DeribitOrderParseStatus.MALFORMED;
    }
}
