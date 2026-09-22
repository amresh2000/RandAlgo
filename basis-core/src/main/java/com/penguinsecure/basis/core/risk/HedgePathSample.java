package com.penguinsecure.basis.core.risk;

/** One same-clock observation; failed claims are new failures since the preceding observation. */
public record HedgePathSample(
        long urgentOldestAgeNanos,
        int urgentOccupancy,
        int urgentCapacity,
        long failedClaims,
        long orderAgentProgressAgeNanos,
        boolean socketLive,
        boolean sessionHealthy,
        boolean rateKnown,
        long hedgeRateAvailable,
        long emergencyRateAvailable,
        long oldestUnknownAgeNanos,
        long fillToWriteP99Nanos,
        long fillToWriteP999Nanos,
        boolean reconciled) {
    public HedgePathSample {
        if (urgentOldestAgeNanos < 0
                || urgentOccupancy < 0
                || urgentCapacity <= 0
                || urgentOccupancy > urgentCapacity
                || failedClaims < 0
                || orderAgentProgressAgeNanos < 0
                || hedgeRateAvailable < 0
                || emergencyRateAvailable < 0
                || oldestUnknownAgeNanos < 0
                || fillToWriteP99Nanos < 0
                || fillToWriteP999Nanos < fillToWriteP99Nanos) {
            throw new IllegalArgumentException("invalid hedge-path sample");
        }
    }
}
