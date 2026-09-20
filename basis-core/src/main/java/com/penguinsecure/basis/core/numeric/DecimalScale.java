package com.penguinsecure.basis.core.numeric;

/** Decimal-place contract for a scaled-long value. */
public record DecimalScale(int decimalPlaces) {
    public static final int MAX_DECIMAL_PLACES = 18;

    public DecimalScale {
        if (decimalPlaces < 0 || decimalPlaces > MAX_DECIMAL_PLACES) {
            throw new IllegalArgumentException("decimalPlaces must be in [0, 18]");
        }
    }

    public long factor() {
        return CheckedDecimalMath.powerOfTen(decimalPlaces);
    }
}
