package com.penguinsecure.basis.core.numeric;

/** Unit and decimal scale metadata for a primitive long value. */
public record UnitScale(NumericUnit unit, int decimalPlaces) {
    public UnitScale {
        if (unit == null) {
            throw new IllegalArgumentException("unit is required");
        }
        new DecimalScale(decimalPlaces);
    }
}
