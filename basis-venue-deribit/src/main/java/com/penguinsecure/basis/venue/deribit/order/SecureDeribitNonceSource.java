package com.penguinsecure.basis.venue.deribit.order;

import java.security.SecureRandom;
import java.util.Arrays;

/** Cryptographically random 192-bit hexadecimal nonce source. */
public final class SecureDeribitNonceSource implements DeribitNonceSource {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final SecureRandom random;
    private final byte[] bytes = new byte[24];

    public SecureDeribitNonceSource() {
        this(new SecureRandom());
    }

    SecureDeribitNonceSource(final SecureRandom random) {
        if (random == null) throw new NullPointerException("random is required");
        this.random = random;
    }

    @Override
    public void next(final StringBuilder destination) {
        destination.setLength(0);
        random.nextBytes(bytes);
        for (byte value : bytes) {
            destination.append(HEX[(value >>> 4) & 0xf]);
            destination.append(HEX[value & 0xf]);
        }
        Arrays.fill(bytes, (byte) 0);
    }
}
