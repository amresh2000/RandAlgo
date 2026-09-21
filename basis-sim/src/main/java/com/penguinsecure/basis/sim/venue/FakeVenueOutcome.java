package com.penguinsecure.basis.sim.venue;

public enum FakeVenueOutcome {
    ACCEPT_FULL,
    ACCEPT_PARTIAL,
    REJECT,
    WRITE_FAILED,
    ACCEPT_WITH_LOST_RESPONSE,
    DISCONNECT,
    RATE_LIMIT,
    DUPLICATE_FILL,
    REORDER_FILL_BEFORE_ACK
}
