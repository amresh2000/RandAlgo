package com.penguinsecure.basis.core.book;

/** Venue-neutral mutation semantic carried by a normalized market-data event. */
public enum BookUpdateType {
    IMAGE,
    SNAPSHOT,
    DELTA,
    RESET
}
