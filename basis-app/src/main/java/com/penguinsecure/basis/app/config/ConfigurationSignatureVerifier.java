package com.penguinsecure.basis.app.config;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Closeable HMAC-SHA256 verifier for versioned configuration envelopes. */
public final class ConfigurationSignatureVerifier implements AutoCloseable {
    private byte[] key;

    public ConfigurationSignatureVerifier(final byte[] key) {
        if (key == null || key.length < 16 || key.length > 256)
            throw new IllegalArgumentException("invalid key");
        this.key = key.clone();
    }

    public boolean verify(final SignedConfiguration configuration, final long nowEpochNanos) {
        if (configuration == null
                || key == null
                || nowEpochNanos < configuration.issuedEpochNanos()
                || nowEpochNanos > configuration.expiresEpochNanos()) return false;
        final byte[] actual =
                sign(
                        configuration.formatVersion(),
                        configuration.generation(),
                        configuration.issuedEpochNanos(),
                        configuration.expiresEpochNanos(),
                        configuration.payload());
        final boolean valid =
                java.security.MessageDigest.isEqual(actual, configuration.signature());
        Arrays.fill(actual, (byte) 0);
        return valid;
    }

    public byte[] sign(
            final int version,
            final long generation,
            final long issued,
            final long expires,
            final byte[] payload) {
        if (key == null) throw new IllegalStateException("verifier is closed");
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            final ByteBuffer header =
                    ByteBuffer.allocate(28)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(version)
                            .putLong(generation)
                            .putLong(issued)
                            .putLong(expires);
            mac.update(header.array());
            return mac.doFinal(payload);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }

    @Override
    public void close() {
        if (key != null) Arrays.fill(key, (byte) 0);
        key = null;
    }

    @Override
    public String toString() {
        return "ConfigurationSignatureVerifier[REDACTED]";
    }
}
