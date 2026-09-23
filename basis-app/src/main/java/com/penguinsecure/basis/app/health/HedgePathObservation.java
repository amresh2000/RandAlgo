package com.penguinsecure.basis.app.health;

/** Non-latency fields sampled with one monotonic clock reading for a hedge path. */
public record HedgePathObservation(
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
        boolean reconciled) {}
