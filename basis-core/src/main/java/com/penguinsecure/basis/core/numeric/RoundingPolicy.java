package com.penguinsecure.basis.core.numeric;

/** Explicit rounding policy for a scale-reducing or division operation. */
public enum RoundingPolicy {
    EXACT,
    TOWARD_ZERO,
    AWAY_FROM_ZERO,
    FLOOR,
    CEILING
}
