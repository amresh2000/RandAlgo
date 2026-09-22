package com.penguinsecure.basis.core.identity;

/** Packs restart-fenced identity fields into an unsigned 128-bit primitive pair. */
public final class LocalOrderIdCodec {
    public static final long MAX_SEQUENCE = 0x0000_FFFF_FFFF_FFFFL;

    private LocalOrderIdCodec() {}

    public static void encode(
            int cellId,
            int venueId,
            long sessionGeneration,
            int strategySlot,
            long sequence,
            MutableLocalOrderId destination) {
        requireUnsigned("cellId", cellId, 16);
        requireUnsigned("venueId", venueId, 16);
        requireUnsigned("sessionGeneration", sessionGeneration, 32);
        requireUnsigned("strategySlot", strategySlot, 16);
        if (sequence < 0L || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("sequence exceeds 48 bits");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is required");
        }

        long high = ((long) cellId << 48) | ((long) venueId << 32) | sessionGeneration;
        long low = ((long) strategySlot << 48) | sequence;
        destination.set(high, low);
    }

    public static int cellId(MutableLocalOrderId id) {
        return (int) (id.high() >>> 48);
    }

    public static int venueId(MutableLocalOrderId id) {
        return (int) ((id.high() >>> 32) & 0xFFFFL);
    }

    public static long sessionGeneration(MutableLocalOrderId id) {
        return id.high() & 0xFFFF_FFFFL;
    }

    public static int strategySlot(MutableLocalOrderId id) {
        return (int) (id.low() >>> 48);
    }

    public static long sequence(MutableLocalOrderId id) {
        return id.low() & MAX_SEQUENCE;
    }

    private static void requireUnsigned(String name, long value, int bits) {
        long maximum = (1L << bits) - 1L;
        if (value < 0L || value > maximum) {
            throw new IllegalArgumentException(name + " exceeds " + bits + " bits");
        }
    }
}
