package com.penguinsecure.basis.venue.bybit.order;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.OrderUrgency;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitSecurityAndEncodingTest {
    @Test
    void signsKnownVectorRedactsAndZeroizesCredentials() {
        BybitCredentials credentials = credentials("key", "key");
        StringBuilder signature = new StringBuilder();
        credentials.signAscii("The quick brown fox jumps over the lazy dog", signature);
        assertEquals(
                "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                signature.toString());
        assertFalse(credentials.toString().contains("key"));
        credentials.close();
        assertTrue(credentials.isClosed());
        assertThrows(IllegalStateException.class, () -> credentials.signAscii("x", signature));
    }

    @Test
    void encodesExactIocCreateAndCancelWithoutFloatingPoint() {
        BybitOrderProfile profile = BybitOrderProfile.inverseBtcUsd(7, 11);
        BybitOrderRequestEncoder encoder = new BybitOrderRequestEncoder(profile);
        MutableOrderCommand submit =
                new MutableOrderCommand()
                        .set(
                                OrderCommandType.SUBMIT,
                                OrderUrgency.NORMAL,
                                0x0001_0007_0000_0001L,
                                0x0002_0000_0000_0003L,
                                7,
                                11,
                                OrderSide.BUY,
                                10,
                                8_526_850,
                                1);
        String create = encoder.command(submit, 1_700_000_000_000L).toString();
        assertTrue(create.contains("\"op\":\"order.create\""));
        assertTrue(create.contains("\"qty\":\"10\""));
        assertTrue(create.contains("\"price\":\"85268.50\""));
        assertTrue(create.contains("\"timeInForce\":\"IOC\""));
        assertEquals(2, occurrences(create, "00010007000000010002000000000003"));

        MutableOrderCommand cancel =
                new MutableOrderCommand()
                        .set(
                                OrderCommandType.CANCEL,
                                OrderUrgency.URGENT,
                                submit.localOrderIdHigh(),
                                submit.localOrderIdLow(),
                                7,
                                11,
                                OrderSide.BUY,
                                10,
                                8_526_850,
                                2);
        assertTrue(encoder.command(cancel, 1_700_000_000_001L).toString().contains("order.cancel"));
    }

    @Test
    void signsRestEnvelopeAndRedactsRendering() {
        BybitCredentials credentials = credentials("api", "secret");
        BybitRestRequestSigner signer =
                new BybitRestRequestSigner(BybitOrderProfile.inverseBtcUsd(7, 11));
        MutableBybitRestRequest request = new MutableBybitRestRequest();
        String query = signer.orderQuery(1000, new byte[0], 0);
        signer.sign(
                "GET",
                signer.path(BybitReconciliationEndpoint.OPEN_ORDERS),
                query,
                2000,
                credentials,
                request);
        assertEquals("GET", request.method());
        assertEquals("api", request.apiKey().toString());
        assertEquals(64, request.signature().length());
        assertFalse(request.toString().contains("api"));
        assertFalse(request.toString().contains("secret"));
        request.clearSensitive();
        credentials.close();
    }

    private static BybitCredentials credentials(String key, String secret) {
        return new BybitCredentials(
                key.getBytes(StandardCharsets.US_ASCII),
                secret.getBytes(StandardCharsets.US_ASCII));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
