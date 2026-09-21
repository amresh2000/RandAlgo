package com.penguinsecure.basis.strategy.api.pricing;

import com.penguinsecure.basis.core.numeric.DecimalScale;

/** Venue/account fee rates with explicit maker/taker distinction. */
public record FeeSchedule(long makerRate, long takerRate, int scale, InputMetadata metadata) {
    public FeeSchedule {
        new DecimalScale(scale);
        if (takerRate < 0) {
            throw new IllegalArgumentException("taker rate must be non-negative");
        }
        if (metadata == null) throw new NullPointerException("metadata is required");
    }
}
