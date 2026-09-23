package com.penguinsecure.basis.venue.deribit.order;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.OrderUrgency;
import com.penguinsecure.basis.core.identity.LocalOrderIdCodec;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeribitSecurityAndEncodingTest {
    private final DeribitOrderProfile profile = DeribitOrderProfile.inverseBtcPerpetual(7, 11);

    @Test
    void signsAuthenticationAndNeverSerializesSecret() {
        DeribitCredentials credentials = credentials();
        DeribitOrderRequestEncoder encoder = new DeribitOrderRequestEncoder(profile);
        String json =
                encoder.authentication(1, 1_700_000_000_000L, "fixed-nonce", credentials)
                        .toString();
        assertTrue(
                json.contains(
                        "\"signature\":\"91eb15f6e3dee96533d05b16d072599d275871730f0842902c0f231e91437e9d\""));
        assertTrue(json.contains("\"client_id\":\"client\""));
        assertFalse(json.contains("secret"));
        assertEquals("DeribitCredentials[REDACTED]", credentials.toString());
        credentials.close();
        assertTrue(credentials.isClosed());
    }

    @Test
    void encodesExactNativeIocAndMonotonicIds() {
        MutableLocalOrderId id = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(1, 7, 1, 2, 3, id);
        MutableOrderCommand command =
                new MutableOrderCommand()
                        .set(
                                OrderCommandType.SUBMIT,
                                OrderUrgency.NORMAL,
                                id.high(),
                                id.low(),
                                7,
                                11,
                                OrderSide.BUY,
                                10,
                                8_526_850,
                                1);
        String json = new DeribitOrderRequestEncoder(profile).submit(command, 9).toString();
        assertTrue(json.contains("\"amount\":10"));
        assertTrue(json.contains("\"price\":85268.50"));
        assertTrue(json.contains("\"time_in_force\":\"immediate_or_cancel\""));
        assertTrue(json.contains("\"label\":\""));
        DeribitRequestIdSequence ids = new DeribitRequestIdSequence(40);
        assertEquals(41, ids.next());
        assertEquals(42, ids.next());
    }

    @Test
    void refreshTokenOwnershipIsRedactedAndReplaceable() {
        DeribitTokenState tokens = new DeribitTokenState();
        byte[] access = "access-one".getBytes(StandardCharsets.US_ASCII);
        byte[] refresh = "refresh-one".getBytes(StandardCharsets.US_ASCII);
        assertTrue(tokens.replace(access, access.length, refresh, refresh.length, 60, 1, 1));
        assertEquals("DeribitTokenState[REDACTED]", tokens.toString());
        assertTrue(tokens.available());
        tokens.close();
        assertFalse(tokens.available());
    }

    private static DeribitCredentials credentials() {
        return new DeribitCredentials(
                "client".getBytes(StandardCharsets.US_ASCII),
                "secret".getBytes(StandardCharsets.US_ASCII));
    }
}
