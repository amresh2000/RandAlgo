package com.penguinsecure.basis.venue.deribit.order;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeribitJsonRpcResponseParserTest {
    private final DeribitJsonRpcResponseParser parser = new DeribitJsonRpcResponseParser();
    private final MutableDeribitResponse response = new MutableDeribitResponse();

    @Test
    void parsesTokensIndependentOfFieldOrder() {
        String json =
                "{\"result\":{\"refresh_token\":\"refresh\",\"expires_in\":900,\"access_token\":\"access\"},\"id\":17,\"jsonrpc\":\"2.0\"}";
        assertEquals(DeribitOrderParseStatus.OK, parse(json));
        assertEquals(17, response.requestId());
        assertTrue(response.hasTokens());
        assertEquals(900, response.expiresInSeconds());
        assertEquals(0, response.errorCode());
    }

    @Test
    void parsesRateAndHeartbeatTest() {
        assertEquals(
                DeribitOrderParseStatus.OK,
                parse(
                        "{\"jsonrpc\":\"2.0\",\"id\":3,\"error\":{\"message\":\"too_many_requests\",\"code\":10028}}"));
        assertEquals(10028, response.errorCode());
        assertEquals(
                DeribitOrderParseStatus.OK,
                parse(
                        "{\"jsonrpc\":\"2.0\",\"params\":{\"type\":\"test_request\"},\"method\":\"heartbeat\"}"));
        assertTrue(response.heartbeatTest());
    }

    private DeribitOrderParseStatus parse(final String json) {
        return parser.parse(
                Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.US_ASCII)), response);
    }
}
