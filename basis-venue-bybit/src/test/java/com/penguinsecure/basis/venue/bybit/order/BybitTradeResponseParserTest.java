package com.penguinsecure.basis.venue.bybit.order;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitTradeResponseParserTest {
    private final BybitTradeResponseParser parser = new BybitTradeResponseParser();
    private final MutableBybitTradeResponse response = new MutableBybitTradeResponse();

    @Test
    void parsesAuthenticationAcceptanceAndRateFeedback() {
        assertEquals(
                BybitOrderParseStatus.OK,
                parse("{\"retCode\":0,\"op\":\"auth\",\"connId\":\"x\"}"));
        assertEquals(BybitTradeResponseKind.AUTHENTICATED, response.kind());

        String command =
                "{\"reqId\":\"00010007000000010002000000000003\",\"retCode\":10006,"
                        + "\"retMsg\":\"Too many visits!\",\"op\":\"order.create\","
                        + "\"header\":{\"X-Bapi-Limit\":\"10\","
                        + "\"X-Bapi-Limit-Status\":\"0\","
                        + "\"X-Bapi-Limit-Reset-Timestamp\":\"1700\"}}";
        assertEquals(BybitOrderParseStatus.OK, parse(command));
        assertEquals(BybitTradeResponseKind.RATE_LIMITED, response.kind());
        assertEquals(10, response.rateLimit());
        assertEquals(1700, response.rateResetMillis());
    }

    @Test
    void rejectsMissingOrMalformedIdentity() {
        assertEquals(
                BybitOrderParseStatus.INVALID_IDENTITY,
                parse("{\"retCode\":0,\"op\":\"order.create\"}"));
        assertEquals(
                BybitOrderParseStatus.INVALID_IDENTITY,
                parse("{\"reqId\":\"bad\",\"retCode\":0,\"op\":\"order.create\"}"));
    }

    private BybitOrderParseStatus parse(String json) {
        return parser.parse(
                Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.US_ASCII)), response);
    }
}
