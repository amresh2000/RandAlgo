package com.penguinsecure.basis.venue.deribit.order;

/** Explicit outcome of bounded Deribit order/private message processing. */
public enum DeribitOrderParseStatus {
    OK,
    IGNORED,
    MALFORMED,
    MISSING_REQUIRED_FIELD,
    DUPLICATE_REQUIRED_FIELD,
    UNSUPPORTED_MESSAGE,
    INVALID_NUMBER,
    INVALID_IDENTITY,
    VENUE_REJECTED,
    DUPLICATE,
    CONFLICT,
    CAPACITY_EXHAUSTED
}
