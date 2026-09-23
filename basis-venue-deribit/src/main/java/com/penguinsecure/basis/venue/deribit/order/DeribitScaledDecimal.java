package com.penguinsecure.basis.venue.deribit.order;

final class DeribitScaledDecimal {
    private DeribitScaledDecimal() {}

    static void append(final StringBuilder destination, final long value, final int scale) {
        if (value < 0 || scale < 0 || scale > 18)
            throw new IllegalArgumentException("invalid scaled value");
        final int start = destination.length();
        destination.append(value);
        if (scale == 0) return;
        final int digits = destination.length() - start;
        if (digits <= scale) {
            destination.insert(start, "0".repeat(scale - digits + 1));
        }
        destination.insert(destination.length() - scale, '.');
    }
}
