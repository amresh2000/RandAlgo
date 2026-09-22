package com.penguinsecure.basis.strategy.api.pricing;

import com.penguinsecure.basis.core.numeric.DecimalScale;

/** Buy/long and sell/short rates sharing one certified evidence generation. */
public record DirectionalRateSchedule(
        long buyRate, long sellRate, int scale, InputMetadata metadata) {
    public DirectionalRateSchedule {
        new DecimalScale(scale);
        if (metadata == null) throw new NullPointerException("metadata is required");
    }

    public long rate(final boolean buy) {
        return buy ? buyRate : sellRate;
    }
}
