package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;
import com.penguinsecure.basis.venue.api.json.MutableJsonLong;
import com.penguinsecure.basis.venue.api.order.MutableVenueOrderFact;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactSink;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactType;
import io.netty.buffer.ByteBuf;

/** Allocation-free normalizer for Bybit inverse order and execution private topics. */
public final class BybitPrivateStreamParser {
    private enum Topic {
        ORDER,
        EXECUTION,
        UNSUPPORTED
    }

    private final BybitOrderProfile profile;
    private final BybitExecutionDeduplicator executions;
    private final BybitActiveOrderTable activeOrders;
    private final BybitRequestCorrelationTable correlations;
    private final JsonByteCursor cursor = new JsonByteCursor(12, 128);
    private final BybitOrderByteBufInput input = new BybitOrderByteBufInput();
    private final ByteToken field = new ByteToken();
    private final ByteToken value = new ByteToken();
    private final ByteToken category = new ByteToken();
    private final ByteToken symbol = new ByteToken();
    private final ByteToken orderLinkId = new ByteToken();
    private final ByteToken orderStatus = new ByteToken();
    private final ByteToken executionId = new ByteToken();
    private final MutableJsonLong number = new MutableJsonLong();
    private final MutableLocalOrderId localId = new MutableLocalOrderId();
    private final byte[] identity = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
    private final MutableVenueOrderFact fact = new MutableVenueOrderFact();
    private int factsPublished;
    private int duplicates;

    public BybitPrivateStreamParser(
            final BybitOrderProfile profile,
            final BybitActiveOrderTable activeOrders,
            final BybitRequestCorrelationTable correlations,
            final int executionCapacity) {
        if (profile == null || activeOrders == null || correlations == null)
            throw new NullPointerException("profile and order tables are required");
        this.profile = profile;
        this.activeOrders = activeOrders;
        this.correlations = correlations;
        executions = new BybitExecutionDeduplicator(executionCapacity);
    }

    public BybitOrderParseStatus parse(
            final ByteBuf frame,
            final long expectedSessionGeneration,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final VenueOrderFactSink sink) {
        if (frame == null || sink == null) throw new NullPointerException("arguments are required");
        if (expectedSessionGeneration <= 0 || receiveEpochNanos <= 0 || receiveMonoNanos <= 0) {
            return BybitOrderParseStatus.INVALID_NUMBER;
        }
        input.wrap(frame);
        final Topic topic = detectTopic(frame);
        if (topic == Topic.UNSUPPORTED) return BybitOrderParseStatus.UNSUPPORTED_MESSAGE;
        cursor.reset(input, frame.readerIndex(), frame.readableBytes());
        if (!cursor.consume((byte) '{')) return BybitOrderParseStatus.MALFORMED;
        boolean dataSeen = false;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':')) {
                return BybitOrderParseStatus.MALFORMED;
            }
            if (cursor.tokenEquals(field, "data")) {
                if (dataSeen) return BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD;
                dataSeen = true;
                final BybitOrderParseStatus status =
                        parseData(
                                topic,
                                expectedSessionGeneration,
                                receiveEpochNanos,
                                receiveMonoNanos,
                                sink);
                if (status != BybitOrderParseStatus.OK) return status;
            } else if (!cursor.skipValue()) return BybitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return BybitOrderParseStatus.MALFORMED;
        }
        if (!cursor.consume((byte) '}') || !cursor.atEnd()) return BybitOrderParseStatus.MALFORMED;
        return dataSeen ? BybitOrderParseStatus.OK : BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
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
            if (cursor.tokenEquals(field, "topic")) {
                if (!cursor.readAsciiString(value)) return Topic.UNSUPPORTED;
                if (cursor.tokenEquals(value, "order")
                        || cursor.tokenEquals(value, "order.inverse")) {
                    return Topic.ORDER;
                }
                if (cursor.tokenEquals(value, "execution")
                        || cursor.tokenEquals(value, "execution.inverse")
                        || cursor.tokenEquals(value, "execution.fast")) return Topic.EXECUTION;
                return Topic.UNSUPPORTED;
            }
            if (!cursor.skipValue()) return Topic.UNSUPPORTED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return Topic.UNSUPPORTED;
        }
        return Topic.UNSUPPORTED;
    }

    private BybitOrderParseStatus parseData(
            final Topic topic,
            final long sessionGeneration,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final VenueOrderFactSink sink) {
        if (!cursor.consume((byte) '[')) return BybitOrderParseStatus.MALFORMED;
        while (cursor.peek() != ']') {
            final BybitOrderParseStatus status =
                    parseObject(
                            topic, sessionGeneration, receiveEpochNanos, receiveMonoNanos, sink);
            if (status != BybitOrderParseStatus.OK) return status;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != ']') return BybitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) ']');
        return BybitOrderParseStatus.OK;
    }

    private BybitOrderParseStatus parseObject(
            final Topic topic,
            final long sessionGeneration,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final VenueOrderFactSink sink) {
        if (!cursor.consume((byte) '{')) return BybitOrderParseStatus.MALFORMED;
        boolean hasCategory = false;
        boolean hasSymbol = false;
        boolean hasOrderLinkId = false;
        boolean hasStatus = false;
        boolean hasExecutionId = false;
        boolean hasPrice = false;
        boolean hasQuantity = false;
        long price = 0;
        long quantity = 0;
        while (cursor.peek() != '}') {
            if (!cursor.readAsciiString(field) || !cursor.consume((byte) ':')) {
                return BybitOrderParseStatus.MALFORMED;
            }
            if (cursor.tokenEquals(field, "category")) {
                if (hasCategory || !cursor.readAsciiString(category))
                    return duplicateOrMalformed(hasCategory);
                hasCategory = true;
            } else if (cursor.tokenEquals(field, "symbol")) {
                if (hasSymbol || !cursor.readAsciiString(symbol))
                    return duplicateOrMalformed(hasSymbol);
                hasSymbol = true;
            } else if (cursor.tokenEquals(field, "orderLinkId")) {
                if (hasOrderLinkId || !cursor.readAsciiString(orderLinkId))
                    return duplicateOrMalformed(hasOrderLinkId);
                hasOrderLinkId = true;
            } else if (cursor.tokenEquals(field, "orderStatus")) {
                if (hasStatus || !cursor.readAsciiString(orderStatus))
                    return duplicateOrMalformed(hasStatus);
                hasStatus = true;
            } else if (cursor.tokenEquals(field, "execId")) {
                if (hasExecutionId || !cursor.readAsciiString(executionId))
                    return duplicateOrMalformed(hasExecutionId);
                hasExecutionId = true;
            } else if (cursor.tokenEquals(field, "execPrice")) {
                if (hasPrice || !cursor.readScaledDecimalString(profile.priceScale(), number))
                    return duplicateOrMalformed(hasPrice);
                price = number.value();
                hasPrice = true;
            } else if (cursor.tokenEquals(field, "execQty")) {
                if (hasQuantity || !cursor.readScaledDecimalString(profile.quantityScale(), number))
                    return duplicateOrMalformed(hasQuantity);
                quantity = number.value();
                hasQuantity = true;
            } else if (!cursor.skipValue()) return BybitOrderParseStatus.MALFORMED;
            if (cursor.peek() == ',') cursor.consume((byte) ',');
            else if (cursor.peek() != '}') return BybitOrderParseStatus.MALFORMED;
        }
        cursor.consume((byte) '}');
        if (!hasCategory || !hasSymbol || !hasOrderLinkId) {
            return BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
        }
        if (!cursor.tokenEquals(category, profile.category())
                || !cursor.tokenEquals(symbol, profile.symbol())) {
            return BybitOrderParseStatus.UNSUPPORTED_MESSAGE;
        }
        if (!decodeIdentity(orderLinkId, sessionGeneration)) {
            return BybitOrderParseStatus.INVALID_IDENTITY;
        }
        if (topic == Topic.ORDER) {
            if (!hasStatus) return BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
            final VenueOrderFactType type = orderFactType();
            if (type == null) return BybitOrderParseStatus.OK;
            final BybitOrderParseStatus status =
                    publish(
                            type,
                            0,
                            0,
                            0,
                            sessionGeneration,
                            receiveEpochNanos,
                            receiveMonoNanos,
                            sink);
            if (status == BybitOrderParseStatus.OK) {
                correlations.complete(localId.high(), localId.low(), sessionGeneration);
            }
            if (status == BybitOrderParseStatus.OK
                    && (type == VenueOrderFactType.CANCELLED
                            || type == VenueOrderFactType.REJECTED)) {
                activeOrders.remove(localId.high(), localId.low());
            }
            return status;
        }
        if (!hasExecutionId || !hasPrice || !hasQuantity || price <= 0 || quantity <= 0) {
            return BybitOrderParseStatus.MISSING_REQUIRED_FIELD;
        }
        final BybitExecutionDeduplicator.Result result =
                executions.admit(
                        cursor, executionId, localId.high(), localId.low(), quantity, price);
        if (result == BybitExecutionDeduplicator.Result.DUPLICATE) {
            duplicates++;
            return BybitOrderParseStatus.OK;
        }
        if (result == BybitExecutionDeduplicator.Result.CONFLICT)
            return BybitOrderParseStatus.CONFLICT;
        if (result == BybitExecutionDeduplicator.Result.CAPACITY_EXHAUSTED) {
            return BybitOrderParseStatus.CAPACITY_EXHAUSTED;
        }
        if (!activeOrders.addFill(localId.high(), localId.low(), quantity))
            return BybitOrderParseStatus.CONFLICT;
        correlations.complete(localId.high(), localId.low(), sessionGeneration);
        return publish(
                VenueOrderFactType.FILL,
                cursor.tokenHash64(executionId),
                quantity,
                price,
                sessionGeneration,
                receiveEpochNanos,
                receiveMonoNanos,
                sink);
    }

    private boolean decodeIdentity(final ByteToken token, final long sessionGeneration) {
        return token.length() == identity.length
                && cursor.copyToken(token, identity, 0)
                && VenueClientIdEncoder.decode(identity, 0, identity.length, localId)
                && (localId.high() & 0xffff_ffffL) == sessionGeneration
                && ((localId.high() >>> 32) & 0xffffL) == profile.venueId();
    }

    private VenueOrderFactType orderFactType() {
        if (cursor.tokenEquals(orderStatus, "New") || cursor.tokenEquals(orderStatus, "Created")) {
            return VenueOrderFactType.ACKNOWLEDGED;
        }
        if (cursor.tokenEquals(orderStatus, "Cancelled")
                || cursor.tokenEquals(orderStatus, "PartiallyFilledCanceled")
                || cursor.tokenEquals(orderStatus, "Deactivated"))
            return VenueOrderFactType.CANCELLED;
        if (cursor.tokenEquals(orderStatus, "Rejected")) return VenueOrderFactType.REJECTED;
        return null;
    }

    private BybitOrderParseStatus publish(
            final VenueOrderFactType type,
            final long executionHash,
            final long quantity,
            final long price,
            final long sessionGeneration,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final VenueOrderFactSink sink) {
        fact.set(
                type,
                localId.high(),
                localId.low(),
                profile.venueId(),
                profile.instrumentId(),
                sessionGeneration,
                receiveEpochNanos,
                receiveMonoNanos,
                executionHash,
                quantity,
                price,
                0,
                null,
                0);
        if (!sink.publish(fact)) return BybitOrderParseStatus.CAPACITY_EXHAUSTED;
        factsPublished++;
        return BybitOrderParseStatus.OK;
    }

    private static BybitOrderParseStatus duplicateOrMalformed(final boolean duplicate) {
        return duplicate
                ? BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD
                : BybitOrderParseStatus.MALFORMED;
    }
}
