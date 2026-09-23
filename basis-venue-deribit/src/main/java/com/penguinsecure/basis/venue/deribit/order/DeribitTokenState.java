package com.penguinsecure.basis.venue.deribit.order;

import java.util.Arrays;

/** Closeable OAuth token owner; replacements zero the previous token bytes. */
public final class DeribitTokenState implements AutoCloseable {
    private byte[] accessToken = new byte[0];
    private byte[] refreshToken = new byte[0];
    private int accessLength;
    private int refreshLength;
    private long refreshAtEpochNanos;

    public boolean replace(
            final byte[] access,
            final int newAccessLength,
            final byte[] refresh,
            final int newRefreshLength,
            final long expiresInSeconds,
            final long nowEpochNanos,
            final long refreshMarginNanos) {
        if (!safe(access, newAccessLength, 4096)
                || !safe(refresh, newRefreshLength, 4096)
                || expiresInSeconds <= 0
                || nowEpochNanos <= 0
                || refreshMarginNanos < 0
                || expiresInSeconds > Long.MAX_VALUE / 1_000_000_000L) return false;
        final long lifetime = expiresInSeconds * 1_000_000_000L;
        if (lifetime <= refreshMarginNanos || nowEpochNanos > Long.MAX_VALUE - lifetime)
            return false;
        clear();
        accessToken = Arrays.copyOf(access, newAccessLength);
        refreshToken = Arrays.copyOf(refresh, newRefreshLength);
        accessLength = newAccessLength;
        refreshLength = newRefreshLength;
        refreshAtEpochNanos = nowEpochNanos + lifetime - refreshMarginNanos;
        return true;
    }

    public boolean available() {
        return accessLength > 0 && refreshLength > 0;
    }

    public boolean refreshRequired(final long epochNanos) {
        return available() && epochNanos >= refreshAtEpochNanos;
    }

    public void appendAccessToken(final StringBuilder destination) {
        if (!available()) throw new IllegalStateException("token is unavailable");
        append(destination, accessToken, accessLength);
    }

    public void appendRefreshToken(final StringBuilder destination) {
        if (!available()) throw new IllegalStateException("token is unavailable");
        append(destination, refreshToken, refreshLength);
    }

    @Override
    public void close() {
        clear();
    }

    @Override
    public String toString() {
        return "DeribitTokenState[REDACTED]";
    }

    private void clear() {
        Arrays.fill(accessToken, (byte) 0);
        Arrays.fill(refreshToken, (byte) 0);
        accessToken = new byte[0];
        refreshToken = new byte[0];
        accessLength = 0;
        refreshLength = 0;
        refreshAtEpochNanos = 0;
    }

    private static void append(
            final StringBuilder destination, final byte[] value, final int length) {
        for (int index = 0; index < length; index++) destination.append((char) value[index]);
    }

    private static boolean safe(final byte[] value, final int length, final int maximum) {
        if (value == null || length <= 0 || length > value.length || length > maximum) return false;
        for (int index = 0; index < length; index++) {
            final byte character = value[index];
            if (character < 0x21 || character > 0x7e) return false;
        }
        return true;
    }
}
