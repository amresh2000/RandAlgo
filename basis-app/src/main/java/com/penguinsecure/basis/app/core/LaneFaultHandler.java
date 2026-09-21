package com.penguinsecure.basis.app.core;

import com.penguinsecure.basis.venue.api.lane.LaneHealthSnapshot;

/** Receives unhealthy producer state before any market event in the same duty cycle. */
@FunctionalInterface
public interface LaneFaultHandler {
    void onLaneFault(int laneIndex, LaneHealthSnapshot health);
}
