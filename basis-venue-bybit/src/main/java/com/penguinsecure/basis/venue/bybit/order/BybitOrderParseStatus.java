package com.penguinsecure.basis.venue.bybit.order;

/** Explicit authenticated-wire parsing and normalization outcomes. */
public enum BybitOrderParseStatus {
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
