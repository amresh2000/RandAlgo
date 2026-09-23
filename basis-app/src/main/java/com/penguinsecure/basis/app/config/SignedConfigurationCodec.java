package com.penguinsecure.basis.app.config;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Bounded binary envelope used by the authenticated configuration-stage command. */
public final class SignedConfigurationCodec {
    public static final int HEADER_LENGTH = Integer.BYTES + 3 * Long.BYTES + Integer.BYTES;
    public static final int SIGNATURE_LENGTH = 32;

    private SignedConfigurationCodec() {}

    public static SignedConfiguration decode(final byte[] source, final int length) {
        if (source == null
                || length < HEADER_LENGTH + 1 + SIGNATURE_LENGTH
                || length > source.length) {
            return null;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(source, 0, length).order(ByteOrder.LITTLE_ENDIAN);
        final int version = buffer.getInt();
        final long generation = buffer.getLong();
        final long issued = buffer.getLong();
        final long expires = buffer.getLong();
        final int payloadLength = buffer.getInt();
        if (payloadLength <= 0 || payloadLength != buffer.remaining() - SIGNATURE_LENGTH) {
            return null;
        }
        final byte[] payload = new byte[payloadLength];
        final byte[] signature = new byte[SIGNATURE_LENGTH];
        buffer.get(payload).get(signature);
        try {
            return new SignedConfiguration(
                    version, generation, issued, expires, payload, signature);
        } catch (IllegalArgumentException exception) {
            Arrays.fill(payload, (byte) 0);
            Arrays.fill(signature, (byte) 0);
            return null;
        }
    }

    public static byte[] encode(final SignedConfiguration configuration) {
        final byte[] payload = configuration.payload();
        final byte[] signature = configuration.signature();
        return ByteBuffer.allocate(HEADER_LENGTH + payload.length + signature.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(configuration.formatVersion())
                .putLong(configuration.generation())
                .putLong(configuration.issuedEpochNanos())
                .putLong(configuration.expiresEpochNanos())
                .putInt(payload.length)
                .put(payload)
                .put(signature)
                .array();
    }
}
