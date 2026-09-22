package com.penguinsecure.basis.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class LocalOrderIdCodecTest {
    @Test
    void roundTripsMaximumFieldsThroughVenueEncoding() {
        MutableLocalOrderId original = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(
                65_535, 65_535, 0xFFFF_FFFFL, 65_535, LocalOrderIdCodec.MAX_SEQUENCE, original);
        byte[] encoded = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
        assertEquals(32, VenueClientIdEncoder.encode(original, encoded, 0));

        MutableLocalOrderId decoded = new MutableLocalOrderId();
        assertTrue(VenueClientIdEncoder.decode(encoded, 0, encoded.length, decoded));
        assertEquals(original.high(), decoded.high());
        assertEquals(original.low(), decoded.low());
        assertEquals(65_535, LocalOrderIdCodec.cellId(decoded));
        assertEquals(0xFFFF_FFFFL, LocalOrderIdCodec.sessionGeneration(decoded));
        assertEquals(LocalOrderIdCodec.MAX_SEQUENCE, LocalOrderIdCodec.sequence(decoded));
    }

    @Test
    void sessionGenerationFencesRestartAndBoundsAreEnforced() {
        MutableLocalOrderId first = new MutableLocalOrderId();
        MutableLocalOrderId restarted = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(1, 2, 3, 4, 5, first);
        LocalOrderIdCodec.encode(1, 2, 4, 4, 5, restarted);
        assertNotEquals(first.high(), restarted.high());
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalOrderIdCodec.encode(65_536, 2, 3, 4, 5, first));
        assertEquals(-1, VenueClientIdEncoder.encode(first, new byte[31], 0));
    }

    @Test
    void malformedHexIsRejected() {
        byte[] encoded =
                "0000000000000000000000000000000z"
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertFalse(
                VenueClientIdEncoder.decode(encoded, 0, encoded.length, new MutableLocalOrderId()));
    }
}
