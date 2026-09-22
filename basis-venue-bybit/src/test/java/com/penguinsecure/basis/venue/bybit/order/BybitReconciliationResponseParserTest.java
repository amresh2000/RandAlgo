package com.penguinsecure.basis.venue.bybit.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.identity.LocalOrderIdCodec;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;
import com.penguinsecure.basis.venue.api.order.VenueOrderState;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitReconciliationResponseParserTest {
    @Test
    void parsesBoundedAuthoritativeOrderPageAndCursor() {
        BybitOrderProfile profile = BybitOrderProfile.inverseBtcUsd(7, 11);
        MutableLocalOrderId id = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(1, 7, 3, 2, 9, id);
        byte[] encoded = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
        VenueClientIdEncoder.encode(id, encoded, 0);
        String json =
                "{\"result\":{\"category\":\"inverse\",\"nextPageCursor\":\"abc+=\","
                        + "\"list\":[{\"orderStatus\":\"PartiallyFilled\",\"cumExecQty\":\"4\","
                        + "\"orderLinkId\":\""
                        + new String(encoded, StandardCharsets.US_ASCII)
                        + "\"}]},\"retCode\":0}";
        byte[] bytes = json.getBytes(StandardCharsets.US_ASCII);
        BybitReconciliationPage page = new BybitReconciliationPage(2, 16);

        assertEquals(
                BybitOrderParseStatus.OK,
                new BybitReconciliationResponseParser(profile).parse(bytes, bytes.length, 3, page));
        assertEquals(1, page.size());
        assertEquals(id.high(), page.idHigh(0));
        assertEquals(4, page.filled(0));
        assertEquals(VenueOrderState.PARTIALLY_FILLED, page.state(0));
        assertEquals(5, page.nextCursorLength());
        assertEquals(
                "category=inverse&symbol=BTCUSD&limit=50&cursor=abc%2B%3D",
                new BybitRestRequestSigner(profile)
                        .orderQuery(0, page.nextCursorBytes(), page.nextCursorLength()));
    }

    @Test
    void admitsOlderOwnedGenerationForRecoveryAndReportsVenueRejection() {
        BybitOrderProfile profile = BybitOrderProfile.inverseBtcUsd(7, 11);
        MutableLocalOrderId id = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(1, 7, 3, 2, 9, id);
        byte[] encoded = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
        VenueClientIdEncoder.encode(id, encoded, 0);
        byte[] wrongGeneration =
                ("{\"retCode\":0,\"result\":{\"list\":[{\"orderLinkId\":\""
                                + new String(encoded, StandardCharsets.US_ASCII)
                                + "\",\"orderStatus\":\"New\",\"cumExecQty\":\"0\"}]}}")
                        .getBytes(StandardCharsets.US_ASCII);
        BybitReconciliationResponseParser parser = new BybitReconciliationResponseParser(profile);
        assertEquals(
                BybitOrderParseStatus.OK,
                parser.parse(
                        wrongGeneration,
                        wrongGeneration.length,
                        4,
                        new BybitReconciliationPage(2, 16)));

        byte[] rejected =
                "{\"retCode\":10006,\"result\":{\"list\":[]}}".getBytes(StandardCharsets.US_ASCII);
        assertEquals(
                BybitOrderParseStatus.VENUE_REJECTED,
                parser.parse(rejected, rejected.length, 3, new BybitReconciliationPage(2, 16)));
    }
}
