package com.penguinsecure.basis.venue.bybit.order;

/** Exact scaled-long JSON decimal formatting without floating point. */
final class BybitScaledDecimal {
    private BybitScaledDecimal() {}

    static void append(final StringBuilder destination, final long value, final int scale) {
        if (value < 0 || scale < 0 || scale > 18) {
            throw new IllegalArgumentException("invalid unsigned scaled decimal");
        }
        final int start = destination.length();
        destination.append(value);
        final int digits = destination.length() - start;
        if (scale == 0) {
            return;
        } else if (digits <= scale) {
            destination.insert(start, '0');
            destination.insert(start + 1, '.');
            for (int index = 0; index < scale - digits; index++) {
                destination.insert(start + 2, '0');
            }
        } else {
            destination.insert(start + digits - scale, '.');
        }
    }
}
