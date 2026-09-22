package com.penguinsecure.basis.venue.api.order;

/** Venue-neutral order evidence emitted by authenticated adapters. */
public enum VenueOrderFactType {
    WRITE_ACCEPTED,
    WRITE_FAILED,
    WRITE_AMBIGUOUS,
    RATE_LIMITED,
    DISCONNECTED,
    ACKNOWLEDGED,
    FILL,
    CANCELLED,
    REJECTED,
    RECONCILED
}
