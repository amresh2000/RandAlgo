package com.penguinsecure.basis.core.identity;

/** Allocation-free, fixed-width hexadecimal venue client-ID encoding. */
public final class VenueClientIdEncoder {
    public static final int ENCODED_LENGTH = 32;
    private static final byte[] HEX =
            "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private VenueClientIdEncoder() {}

    public static int encode(MutableLocalOrderId id, byte[] destination, int offset) {
        if (id == null
                || destination == null
                || offset < 0
                || destination.length - offset < ENCODED_LENGTH) {
            return -1;
        }
        writeHex(id.high(), destination, offset);
        writeHex(id.low(), destination, offset + 16);
        return ENCODED_LENGTH;
    }

    public static boolean decode(
            byte[] source, int offset, int length, MutableLocalOrderId destination) {
        if (source == null
                || destination == null
                || offset < 0
                || length != ENCODED_LENGTH
                || source.length - offset < length) {
            return false;
        }
        if (!isHex(source, offset) || !isHex(source, offset + 16)) {
            return false;
        }
        long high = parseHex(source, offset);
        long low = parseHex(source, offset + 16);
        destination.set(high, low);
        return true;
    }

    private static void writeHex(long value, byte[] destination, int offset) {
        for (int index = 15; index >= 0; index--) {
            destination[offset + index] = HEX[(int) (value & 0x0F)];
            value >>>= 4;
        }
    }

    private static long parseHex(byte[] source, int offset) {
        long value = 0L;
        for (int index = 0; index < 16; index++) {
            int digit = Character.digit((char) source[offset + index], 16);
            value = (value << 4) | digit;
        }
        return value;
    }

    private static boolean isHex(byte[] source, int offset) {
        for (int index = 0; index < 16; index++) {
            if (Character.digit((char) source[offset + index], 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
