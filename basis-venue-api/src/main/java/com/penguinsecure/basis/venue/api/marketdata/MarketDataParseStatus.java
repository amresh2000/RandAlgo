package com.penguinsecure.basis.venue.api.marketdata;

/** Allocation-free parser result; wire faults are data, not exceptions. */
public enum MarketDataParseStatus {
    OK,
    MALFORMED,
    MISSING_REQUIRED_FIELD,
    DUPLICATE_REQUIRED_FIELD,
    UNSUPPORTED_MESSAGE,
    UNSUPPORTED_PROFILE,
    INVALID_NUMBER,
    TOO_MANY_LEVELS,
    TOKEN_TOO_LONG,
    DEPTH_EXCEEDED
}
