package com.penguinsecure.basis.venue.bybit.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitPrivateControlParserTest {
    private final BybitPrivateControlParser parser = new BybitPrivateControlParser();
    private final MutableBybitPrivateControl result = new MutableBybitPrivateControl();

    @Test
    void distinguishesAuthenticationAndSubscriptionResults() {
        assertControl(
                "{\"success\":true,\"ret_msg\":\"\",\"op\":\"auth\",\"conn_id\":\"x\"}",
                BybitPrivateControlKind.AUTHENTICATED);
        assertControl(
                "{\"op\":\"subscribe\",\"success\":true}", BybitPrivateControlKind.SUBSCRIBED);
        assertControl(
                "{\"success\":false,\"op\":\"subscribe\",\"ret_msg\":\"denied\"}",
                BybitPrivateControlKind.SUBSCRIPTION_FAILED);
    }

    @Test
    void rejectsMissingAndDuplicateSuccess() {
        assertEquals(BybitOrderParseStatus.MISSING_REQUIRED_FIELD, parse("{\"op\":\"auth\"}"));
        assertEquals(
                BybitOrderParseStatus.DUPLICATE_REQUIRED_FIELD,
                parse("{\"success\":true,\"success\":false,\"op\":\"auth\"}"));
    }

    private void assertControl(final String json, final BybitPrivateControlKind expected) {
        assertEquals(BybitOrderParseStatus.OK, parse(json));
        assertEquals(expected, result.kind());
    }

    private BybitOrderParseStatus parse(final String json) {
        return parser.parse(
                Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.US_ASCII)), result);
    }
}
