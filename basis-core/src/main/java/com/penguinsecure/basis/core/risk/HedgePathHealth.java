package com.penguinsecure.basis.core.risk;

/** Hysteretic hedge-path state; only HEALTHY permits new exposure. */
public final class HedgePathHealth {
    private final HedgePathHealthConfig config;
    private HedgePathHealthState state = HedgePathHealthState.UNSAFE;
    private int consecutiveHealthy;
    private boolean reconciliationRequired = true;

    public HedgePathHealth(final HedgePathHealthConfig config) {
        if (config == null) throw new NullPointerException("config is required");
        this.config = config;
    }

    public HedgePathHealthState observe(final HedgePathSample sample) {
        if (sample == null) throw new NullPointerException("sample is required");
        if (unsafe(sample)) {
            state = HedgePathHealthState.UNSAFE;
            consecutiveHealthy = 0;
            if (!sample.socketLive() || !sample.sessionHealthy() || sample.failedClaims() > 0) {
                reconciliationRequired = true;
            }
            return state;
        }
        if (degraded(sample)) {
            state = HedgePathHealthState.DEGRADED;
            consecutiveHealthy = 0;
            return state;
        }
        if (reconciliationRequired && !sample.reconciled()) {
            state = HedgePathHealthState.RECOVERING;
            consecutiveHealthy = 0;
            return state;
        }
        reconciliationRequired = false;
        if (state == HedgePathHealthState.HEALTHY) return state;
        state = HedgePathHealthState.RECOVERING;
        consecutiveHealthy++;
        if (consecutiveHealthy >= config.healthyObservationsRequired()) {
            state = HedgePathHealthState.HEALTHY;
            consecutiveHealthy = 0;
        }
        return state;
    }

    public HedgePathHealthState state() {
        return state;
    }

    public boolean permitsInitiation() {
        return state == HedgePathHealthState.HEALTHY;
    }

    private boolean unsafe(final HedgePathSample sample) {
        return !sample.socketLive()
                || !sample.sessionHealthy()
                || !sample.rateKnown()
                || sample.hedgeRateAvailable() <= 0
                || sample.emergencyRateAvailable() <= 0
                || sample.failedClaims() > 0
                || sample.urgentOldestAgeNanos() >= config.unsafeQueueAgeNanos()
                || occupancy(sample) >= config.unsafeOccupancyPartsPerMillion()
                || sample.orderAgentProgressAgeNanos() > config.maximumAgentProgressAgeNanos()
                || sample.oldestUnknownAgeNanos() > config.maximumUnknownAgeNanos()
                || sample.fillToWriteP999Nanos() > config.maximumFillToWriteP999Nanos();
    }

    private boolean degraded(final HedgePathSample sample) {
        return sample.urgentOldestAgeNanos() >= config.degradedQueueAgeNanos()
                || occupancy(sample) >= config.degradedOccupancyPartsPerMillion()
                || sample.fillToWriteP99Nanos() > config.maximumFillToWriteP99Nanos();
    }

    private static int occupancy(final HedgePathSample sample) {
        return (int) (((long) sample.urgentOccupancy() * 1_000_000L) / sample.urgentCapacity());
    }
}
