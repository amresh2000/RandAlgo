package com.penguinsecure.basis.core.book;

/** Stable reason why the current book cannot authorize new exposure. */
public enum BookRejectionReason {
    NONE,
    DISCONNECTED,
    RESET,
    SESSION_CHANGED,
    ROUTE_MISMATCH,
    NEED_IMAGE,
    UNSUPPORTED_UPDATE,
    NON_MONOTONIC_SEQUENCE,
    VALIDATION_FLAGS,
    EMPTY_BOOK,
    INVALID_PRICE,
    INVALID_QUANTITY,
    INVALID_TICK,
    INVALID_LOT,
    INVALID_DELETE,
    DUPLICATE_PRICE,
    UNORDERED,
    CROSSED,
    CAPACITY,
    STALE,
    ARITHMETIC_OVERFLOW
}
