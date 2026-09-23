package com.penguinsecure.basis.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class StagedConfigurationStoreTest {
    private static final long NOW = 2_000;
    private static final byte[] KEY =
            "phase-eleven-test-key".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    void validatesSignatureAndMonotonicGenerationBeforeActivation() {
        try (ConfigurationSignatureVerifier verifier = new ConfigurationSignatureVerifier(KEY)) {
            final StagedConfigurationStore store = new StagedConfigurationStore(verifier);
            final SignedConfiguration first = signed(verifier, 4, new byte[] {1, 2, 3});

            assertEquals(ConfigurationStageStatus.STAGED, store.stage(first, NOW));
            assertEquals(ConfigurationStageStatus.ACTIVATED, store.activate(4));
            assertEquals(4, store.activeGeneration());
            assertEquals(ConfigurationStageStatus.STALE_GENERATION, store.stage(first, NOW));

            final SignedConfiguration invalid =
                    new SignedConfiguration(1, 5, 1_000, 3_000, new byte[] {9}, new byte[32]);
            assertEquals(ConfigurationStageStatus.INVALID_SIGNATURE, store.stage(invalid, NOW));
        }
    }

    @Test
    void binaryEnvelopeIsBoundedAndRoundTrips() {
        try (ConfigurationSignatureVerifier verifier = new ConfigurationSignatureVerifier(KEY)) {
            final SignedConfiguration configuration = signed(verifier, 8, new byte[] {4, 5});
            final byte[] encoded = SignedConfigurationCodec.encode(configuration);

            final SignedConfiguration decoded =
                    SignedConfigurationCodec.decode(encoded, encoded.length);
            assertNotNull(decoded);
            assertEquals(8, decoded.generation());
            assertNull(SignedConfigurationCodec.decode(encoded, encoded.length - 1));
        }
    }

    private static SignedConfiguration signed(
            final ConfigurationSignatureVerifier verifier,
            final long generation,
            final byte[] payload) {
        final byte[] signature = verifier.sign(1, generation, 1_000, 3_000, payload);
        return new SignedConfiguration(1, generation, 1_000, 3_000, payload, signature);
    }
}
