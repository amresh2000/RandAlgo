package com.penguinsecure.basis.app.health;

import com.penguinsecure.basis.app.observability.StageLatencyHistogram;
import com.penguinsecure.basis.core.risk.HedgePathHealth;
import com.penguinsecure.basis.core.risk.HedgePathHealthState;
import com.penguinsecure.basis.core.risk.HedgePathSample;

/** Core-owned bridge from stage histograms and path evidence to hysteretic health. */
public final class HedgePathMonitor {
    private final HedgePathHealth health;
    private final StageLatencyHistogram fillToWrite;

    public HedgePathMonitor(final HedgePathHealth health, final StageLatencyHistogram fillToWrite) {
        if (health == null || fillToWrite == null) {
            throw new NullPointerException("dependencies are required");
        }
        this.health = health;
        this.fillToWrite = fillToWrite;
    }

    public HedgePathHealthState observe(final HedgePathObservation observation) {
        if (observation == null) {
            throw new NullPointerException("observation is required");
        }
        final long p99 = fillToWrite.percentile(990_000);
        final long p999 = fillToWrite.percentile(999_000);
        final HedgePathSample sample =
                new HedgePathSample(
                        observation.urgentOldestAgeNanos(),
                        observation.urgentOccupancy(),
                        observation.urgentCapacity(),
                        observation.failedClaims(),
                        observation.orderAgentProgressAgeNanos(),
                        observation.socketLive(),
                        observation.sessionHealthy(),
                        observation.rateKnown(),
                        observation.hedgeRateAvailable(),
                        observation.emergencyRateAvailable(),
                        observation.oldestUnknownAgeNanos(),
                        p99,
                        p999,
                        observation.reconciled());
        final HedgePathHealthState result = health.observe(sample);
        fillToWrite.reset();
        return result;
    }

    public boolean permitsInitiation() {
        return health.permitsInitiation();
    }
}
