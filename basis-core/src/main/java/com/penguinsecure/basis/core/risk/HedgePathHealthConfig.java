package com.penguinsecure.basis.core.risk;

/** Certified thresholds and hysteresis for one hedge venue/session path. */
public record HedgePathHealthConfig(
        long degradedQueueAgeNanos,
        long unsafeQueueAgeNanos,
        int degradedOccupancyPartsPerMillion,
        int unsafeOccupancyPartsPerMillion,
        long maximumAgentProgressAgeNanos,
        long maximumUnknownAgeNanos,
        long maximumFillToWriteP99Nanos,
        long maximumFillToWriteP999Nanos,
        int healthyObservationsRequired) {
    public HedgePathHealthConfig {
        if (degradedQueueAgeNanos <= 0
                || unsafeQueueAgeNanos < degradedQueueAgeNanos
                || degradedOccupancyPartsPerMillion <= 0
                || unsafeOccupancyPartsPerMillion < degradedOccupancyPartsPerMillion
                || unsafeOccupancyPartsPerMillion > 1_000_000
                || maximumAgentProgressAgeNanos <= 0
                || maximumUnknownAgeNanos <= 0
                || maximumFillToWriteP99Nanos <= 0
                || maximumFillToWriteP999Nanos < maximumFillToWriteP99Nanos
                || healthyObservationsRequired <= 0) {
            throw new IllegalArgumentException("invalid hedge-path thresholds");
        }
    }
}
