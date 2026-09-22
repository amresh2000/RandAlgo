package com.penguinsecure.basis.venue.api.lane;

/** Producer state stored outside the bounded data ring. */
public enum LaneHealthState {
    HEALTHY,
    DEGRADED,
    OVERFLOW,
    STOPPED
}
