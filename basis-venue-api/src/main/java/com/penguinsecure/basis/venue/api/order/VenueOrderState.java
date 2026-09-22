package com.penguinsecure.basis.venue.api.order;

/** Authoritative state carried only by reconciliation evidence. */
public enum VenueOrderState {
    WORKING,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED
}
