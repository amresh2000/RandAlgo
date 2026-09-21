package com.penguinsecure.basis.strategy.api.pricing;

import com.penguinsecure.basis.core.numeric.DecimalScale;

/** Multiplicative conversion into the strategy risk currency. */
public record ConversionRate(long value, int scale, InputMetadata metadata) {
    public ConversionRate {
        new DecimalScale(scale);
        if (value <= 0) throw new IllegalArgumentException("conversion rate must be positive");
        if (metadata == null) throw new NullPointerException("metadata is required");
    }
}
