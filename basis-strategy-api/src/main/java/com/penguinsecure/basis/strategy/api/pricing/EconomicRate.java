package com.penguinsecure.basis.strategy.api.pricing;

import com.penguinsecure.basis.core.numeric.DecimalScale;

/** One scaled rate and its mandatory versioned evidence. */
public record EconomicRate(long value, int scale, InputMetadata metadata) {
    public EconomicRate {
        new DecimalScale(scale);
        if (metadata == null) throw new NullPointerException("metadata is required");
    }
}
