package com.penguinsecure.basis.venue.bybit.order;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Secret-owning HMAC boundary. Copies are zeroed when the adapter is closed. */
public final class BybitCredentials implements AutoCloseable {
    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private byte[] apiKey;
    private byte[] secret;

    public BybitCredentials(final byte[] apiKey, final byte[] secret) {
        if (!safe(apiKey, 128) || !safe(secret, 256)) {
            throw new IllegalArgumentException("invalid Bybit credentials");
        }
        this.apiKey = apiKey.clone();
        this.secret = secret.clone();
    }

    public void appendApiKey(final StringBuilder destination) {
        requireOpen();
        for (byte value : apiKey) destination.append((char) value);
    }

    public void signAscii(final CharSequence payload, final StringBuilder destination) {
        requireOpen();
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            for (int i = 0; i < payload.length(); i++) {
                final char value = payload.charAt(i);
                if (value > 0x7f) throw new IllegalArgumentException("payload must be ASCII");
                mac.update((byte) value);
            }
            final byte[] digest = mac.doFinal();
            for (byte value : digest) {
                destination.append((char) HEX[(value >>> 4) & 0x0f]);
                destination.append((char) HEX[value & 0x0f]);
            }
            Arrays.fill(digest, (byte) 0);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }

    public boolean isClosed() {
        return apiKey == null;
    }

    @Override
    public void close() {
        if (apiKey != null) Arrays.fill(apiKey, (byte) 0);
        if (secret != null) Arrays.fill(secret, (byte) 0);
        apiKey = null;
        secret = null;
    }

    @Override
    public String toString() {
        return "BybitCredentials[REDACTED]";
    }

    private void requireOpen() {
        if (isClosed()) throw new IllegalStateException("credentials are closed");
    }

    private static boolean safe(final byte[] value, final int maximumLength) {
        if (value == null || value.length == 0 || value.length > maximumLength) return false;
        for (byte character : value) if (character < 0x21 || character > 0x7e) return false;
        return true;
    }
}
