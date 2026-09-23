package com.penguinsecure.basis.venue.deribit.order;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.identity.LocalOrderIdCodec;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactType;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeribitPrivateStreamParserTest {
    private final DeribitActiveOrderTable active = new DeribitActiveOrderTable(4);
    private final DeribitRequestCorrelationTable correlations =
            new DeribitRequestCorrelationTable(4);
    private final DeribitPrivateStreamParser parser =
            new DeribitPrivateStreamParser(
                    DeribitOrderProfile.inverseBtcPerpetual(7, 11), active, correlations, 8);
    private final List<VenueOrderFactType> facts = new ArrayList<>();
    private String label;

    @BeforeEach
    void identity() {
        MutableLocalOrderId id = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(1, 7, 1, 2, 3, id);
        byte[] bytes = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
        VenueClientIdEncoder.encode(id, bytes, 0);
        label = new String(bytes, StandardCharsets.US_ASCII);
        assertTrue(active.register(id.high(), id.low(), 10));
        assertTrue(correlations.register(41, id.high(), id.low(), 1, OrderCommandType.SUBMIT));
    }

    @Test
    void mapsOrderAndDeduplicatesAuthoritativeTrade() {
        String order =
                "{\"method\":\"subscription\",\"params\":{\"data\":{\"order_id\":\"123\",\"label\":\""
                        + label
                        + "\",\"instrument_name\":\"BTC-PERPETUAL\",\"order_state\":\"open\"},\"channel\":\"user.orders.BTC-PERPETUAL.raw\"}}";
        assertEquals(DeribitOrderParseStatus.OK, parse(order));
        assertEquals(0, correlations.size());
        String trade =
                "{\"params\":{\"channel\":\"user.trades.BTC-PERPETUAL.raw\",\"data\":[{\"trade_id\":\"t-1\",\"order_id\":\"123\",\"instrument_name\":\"BTC-PERPETUAL\",\"amount\":10,\"price\":85268.5}]},\"method\":\"subscription\"}";
        assertEquals(DeribitOrderParseStatus.OK, parse(trade));
        assertEquals(DeribitOrderParseStatus.OK, parse(trade));
        assertEquals(List.of(VenueOrderFactType.ACKNOWLEDGED, VenueOrderFactType.FILL), facts);
        assertEquals(1, parser.duplicates());
        assertEquals(1, active.size());
    }

    @Test
    void rejectsWrongGenerationAndConflictingTrade() {
        String order =
                "{\"params\":{\"channel\":\"user.orders.BTC-PERPETUAL.raw\",\"data\":{\"order_id\":\"x\",\"label\":\""
                        + label
                        + "\",\"instrument_name\":\"BTC-PERPETUAL\",\"order_state\":\"open\"}},\"method\":\"subscription\"}";
        assertEquals(
                DeribitOrderParseStatus.IGNORED,
                parser.parse(
                        Unpooled.wrappedBuffer(order.getBytes(StandardCharsets.US_ASCII)),
                        2,
                        10,
                        11,
                        fact -> true));
    }

    private DeribitOrderParseStatus parse(final String json) {
        return parser.parse(
                Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.US_ASCII)),
                1,
                10,
                11,
                fact -> {
                    facts.add(fact.type());
                    return true;
                });
    }
}
