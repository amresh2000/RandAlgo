package com.penguinsecure.basis.core.book;

/** Outcome of a bounded executable-depth query. */
public enum ExecutablePriceStatus {
    OK,
    PARTIAL,
    INVALID_ARGUMENT,
    UNTRUSTED,
    OVERFLOW
}
