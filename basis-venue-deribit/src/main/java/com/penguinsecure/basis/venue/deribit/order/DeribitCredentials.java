package com.penguinsecure.basis.venue.deribit.order;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Secret-owning Deribit client-signature boundary. */
public final class DeribitCredentials implements AutoCloseable {
    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private byte[] clientId;
    private byte[] secret;

    public DeribitCredentials(final byte[] clientId, final byte[] secret) {
        if (!safe(clientId, 128) || !safe(secret, 256))
            throw new IllegalArgumentException("invalid Deribit credentials");
        this.clientId = clientId.clone();
        this.secret = secret.clone();
    }

    public void appendClientId(final StringBuilder destination) {
        requireOpen();
        for (byte value : clientId) destination.append((char) value);
    }

    public void signAscii(final CharSequence payload, final StringBuilder destination) {
        requireOpen();
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            for (int index = 0; index < payload.length(); index++) {
                final char value = payload.charAt(index);
                if (value > 0x7f) throw new IllegalArgumentException("payload must be ASCII");
                mac.update((byte) value);
            }
            final byte[] digest = mac.doFinal();
            for (byte value : digest) {
                destination.append((char) HEX[(value >>> 4) & 0xf]);
                destination.append((char) HEX[value & 0xf]);
            }
            Arrays.fill(digest, (byte) 0);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }

    public boolean isClosed() {
        return clientId == null;
    }

    @Override
    public void close() {
        if (clientId != null) Arrays.fill(clientId, (byte) 0);
        if (secret != null) Arrays.fill(secret, (byte) 0);
        clientId = null;
        secret = null;
    }

    @Override
    public String toString() {
        return "DeribitCredentials[REDACTED]";
    }

    private void requireOpen() {
        if (isClosed()) throw new IllegalStateException("credentials are closed");
    }

    private static boolean safe(final byte[] value, final int maximum) {
        if (value == null || value.length == 0 || value.length > maximum) return false;
        for (byte character : value) if (character < 0x21 || character > 0x7e) return false;
        return true;
    }
}
