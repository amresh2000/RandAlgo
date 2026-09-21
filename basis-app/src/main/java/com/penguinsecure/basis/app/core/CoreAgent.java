package com.penguinsecure.basis.app.core;

import com.penguinsecure.basis.venue.api.lane.LaneHealthSnapshot;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.MarketDataEventHandler;
import com.penguinsecure.basis.venue.api.lane.MarketDataLane;
import org.agrona.concurrent.Agent;

/** Health-first, quota-bounded round-robin ingress skeleton. */
public final class CoreAgent implements Agent {
    private final MarketDataLane[] marketDataLanes;
    private final LaneHealthSnapshot[] healthSnapshots;
    private final long[] observedHealthVersions;
    private final MarketDataEventHandler marketDataHandler;
    private final LaneFaultHandler faultHandler;
    private final int perLaneQuota;
    private int firstLane;

    public CoreAgent(
            final MarketDataLane[] marketDataLanes,
            final int perLaneQuota,
            final MarketDataEventHandler marketDataHandler,
            final LaneFaultHandler faultHandler) {
        if (marketDataLanes == null || marketDataLanes.length == 0) {
            throw new IllegalArgumentException("at least one market-data lane is required");
        }
        if (perLaneQuota <= 0 || marketDataHandler == null || faultHandler == null) {
            throw new IllegalArgumentException("quota and handlers are required");
        }
        this.marketDataLanes = marketDataLanes.clone();
        this.healthSnapshots = new LaneHealthSnapshot[marketDataLanes.length];
        this.observedHealthVersions = new long[marketDataLanes.length];
        for (int i = 0; i < marketDataLanes.length; i++) {
            if (marketDataLanes[i] == null)
                throw new NullPointerException("lane " + i + " is null");
            healthSnapshots[i] = new LaneHealthSnapshot();
            observedHealthVersions[i] = Long.MIN_VALUE;
        }
        this.perLaneQuota = perLaneQuota;
        this.marketDataHandler = marketDataHandler;
        this.faultHandler = faultHandler;
    }

    @Override
    public int doWork() {
        int work = sampleHealth();
        final int count = marketDataLanes.length;
        for (int step = 0; step < count; step++) {
            final int index = (firstLane + step) % count;
            work += marketDataLanes[index].drain(marketDataHandler, perLaneQuota);
        }
        firstLane = (firstLane + 1) % count;
        return work;
    }

    private int sampleHealth() {
        int faults = 0;
        for (int i = 0; i < marketDataLanes.length; i++) {
            final LaneHealthSnapshot snapshot = healthSnapshots[i];
            marketDataLanes[i].healthWord().read(snapshot);
            if (snapshot.version() != observedHealthVersions[i]) {
                observedHealthVersions[i] = snapshot.version();
            } else {
                continue;
            }
            if (snapshot.state() != LaneHealthState.HEALTHY) {
                faultHandler.onLaneFault(i, snapshot);
                faults++;
            }
        }
        return faults;
    }

    @Override
    public String roleName() {
        return "basis-core";
    }
}
