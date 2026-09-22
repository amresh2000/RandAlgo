package com.penguinsecure.basis.venue.bybit.order;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.identity.LocalOrderIdCodec;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.core.oems.fact.OrderFactType;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.lane.OrderFactLane;
import com.penguinsecure.basis.venue.api.order.OrderFactLaneSink;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitPrivateStreamParserTest {
    private final ManualClock clock = new ManualClock();
    private final OrderFactLane lane = new OrderFactLane(4096, new LaneHealthWord(), clock, 1);
    private final OrderFactLaneSink sink = new OrderFactLaneSink(lane);
    private final BybitActiveOrderTable activeOrders = new BybitActiveOrderTable(4);
    private final BybitRequestCorrelationTable correlations = new BybitRequestCorrelationTable(4);
    private final BybitPrivateStreamParser parser =
            new BybitPrivateStreamParser(
                    BybitOrderProfile.inverseBtcUsd(7, 11), activeOrders, correlations, 8);
    private String clientId;

    @BeforeEach
    void prepareIdentity() {
        MutableLocalOrderId id = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(1, 7, 1, 2, 3, id);
        byte[] encoded = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
        VenueClientIdEncoder.encode(id, encoded, 0);
        assertTrue(activeOrders.register(id.high(), id.low(), 10));
        assertTrue(correlations.register(id.high(), id.low(), 1, OrderCommandType.SUBMIT));
        clientId = new String(encoded, StandardCharsets.US_ASCII);
        clock.nanos = 100;
    }

    @Test
    void publishesAuthoritativeOrderAndDeduplicatedExecutionFacts() {
        String order =
                "{\"topic\":\"order.inverse\",\"creationTime\":1,\"data\":[{"
                        + "\"symbol\":\"BTCUSD\",\"category\":\"inverse\","
                        + "\"orderLinkId\":\""
                        + clientId
                        + "\",\"orderStatus\":\"New\"}]}";
        assertEquals(BybitOrderParseStatus.OK, parse(order));
        assertEquals(0, correlations.size());

        String execution =
                "{\"data\":[{\"execQty\":\"10\",\"execId\":\"exec-1\","
                        + "\"orderLinkId\":\""
                        + clientId
                        + "\",\"category\":\"inverse\","
                        + "\"execPrice\":\"85268.50\",\"symbol\":\"BTCUSD\"}],"
                        + "\"topic\":\"execution.inverse\",\"creationTime\":2}";
        assertEquals(BybitOrderParseStatus.OK, parse(execution));
        assertEquals(BybitOrderParseStatus.OK, parse(execution));
        assertEquals(1, parser.duplicates());

        List<OrderFactType> types = new ArrayList<>();
        lane.drain(fact -> types.add(fact.type()), 10);
        assertEquals(List.of(OrderFactType.ACKNOWLEDGED, OrderFactType.FILL), types);
        assertEquals(2, parser.factsPublished());
    }

    @Test
    void conflictingDuplicateAndWrongGenerationFailClosed() {
        String base =
                "{\"topic\":\"execution\",\"data\":[{\"category\":\"inverse\","
                        + "\"symbol\":\"BTCUSD\",\"orderLinkId\":\""
                        + clientId
                        + "\","
                        + "\"execId\":\"same\",\"execPrice\":\"1.00\",\"execQty\":\"1\"}]}";
        assertEquals(BybitOrderParseStatus.OK, parse(base));
        assertEquals(BybitOrderParseStatus.CONFLICT, parse(base.replace("\"1\"}]", "\"2\"}]")));
        assertEquals(
                BybitOrderParseStatus.INVALID_IDENTITY,
                parser.parse(
                        Unpooled.wrappedBuffer(base.getBytes(StandardCharsets.US_ASCII)),
                        2,
                        10,
                        11,
                        sink));
    }

    private BybitOrderParseStatus parse(String json) {
        return parser.parse(
                Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.US_ASCII)), 1, 10, 11, sink);
    }

    private static final class ManualClock
            implements com.penguinsecure.basis.core.time.MonotonicClock {
        long nanos;

        @Override
        public long nanoTime() {
            return nanos;
        }
    }
}
