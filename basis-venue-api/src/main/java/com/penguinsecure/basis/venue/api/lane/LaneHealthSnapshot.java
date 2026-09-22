package com.penguinsecure.basis.venue.api.lane;

import com.penguinsecure.basis.venue.api.session.VenueFailureReason;

/** Caller-owned view populated with acquire reads from a lane health word. */
public final class LaneHealthSnapshot {
    private LaneHealthState state;
    private VenueFailureReason reason;
    private long producerEpoch;
    private long sessionGeneration;
    private int affectedScope;
    private long version;

    public LaneHealthState state() {
        return state;
    }

    public VenueFailureReason reason() {
        return reason;
    }

    public long producerEpoch() {
        return producerEpoch;
    }

    public long sessionGeneration() {
        return sessionGeneration;
    }

    public int affectedScope() {
        return affectedScope;
    }

    public long version() {
        return version;
    }

    void set(
            final LaneHealthState state,
            final VenueFailureReason reason,
            final long producerEpoch,
            final long sessionGeneration,
            final int affectedScope,
            final long version) {
        this.state = state;
        this.reason = reason;
        this.producerEpoch = producerEpoch;
        this.sessionGeneration = sessionGeneration;
        this.affectedScope = affectedScope;
        this.version = version;
    }
}
