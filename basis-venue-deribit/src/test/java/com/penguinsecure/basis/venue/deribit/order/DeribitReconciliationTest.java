package com.penguinsecure.basis.venue.deribit.order;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.identity.LocalOrderIdCodec;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.venue.api.order.VenueOrderState;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeribitReconciliationTest {
    @Test
    void parsesArrayAndHistoryContinuation() {
        MutableLocalOrderId id = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(3, 7, 1, 2, 3, id);
        byte[] bytes = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
        VenueClientIdEncoder.encode(id, bytes, 0);
        String label = new String(bytes, StandardCharsets.US_ASCII);
        DeribitReconciliationResponseParser parser =
                new DeribitReconciliationResponseParser(
                        DeribitOrderProfile.inverseBtcPerpetual(7, 11));
        DeribitReconciliationPage page = new DeribitReconciliationPage(4);
        String json =
                "{\"jsonrpc\":\"2.0\",\"result\":{\"continuation\":20,\"entries\":[{\"filled_amount\":2,\"order_state\":\"cancelled\",\"label\":\""
                        + label
                        + "\",\"instrument_name\":\"BTC-PERPETUAL\"}]}}";
        byte[] response = json.getBytes(StandardCharsets.US_ASCII);
        assertEquals(DeribitOrderParseStatus.OK, parser.parse(response, response.length, page));
        assertEquals(1, page.size());
        assertEquals(VenueOrderState.CANCELLED, page.state(0));
        assertTrue(page.hasMore());
        assertEquals(20, page.nextOffset());
    }

    @Test
    void coordinatorDoesNotPublishPartialCollection() {
        DeribitReconciliationPage page = new DeribitReconciliationPage(2);
        int[] calls = {0};
        int[] published = {0};
        DeribitReconciliationCoordinator coordinator =
                new DeribitReconciliationCoordinator(
                        DeribitOrderProfile.inverseBtcPerpetual(7, 11),
                        (endpoint, offset, start, destination) -> {
                            calls[0]++;
                            return endpoint == DeribitReconciliationEndpoint.OPEN_ORDERS;
                        },
                        page,
                        fact -> {
                            published[0]++;
                            return true;
                        },
                        () -> 10,
                        () -> 11,
                        2);
        assertEquals(DeribitReconciliationStatus.TRANSPORT_FAILED, coordinator.reconcileOrders(0));
        assertEquals(2, calls[0]);
        assertEquals(0, published[0]);
    }
}
