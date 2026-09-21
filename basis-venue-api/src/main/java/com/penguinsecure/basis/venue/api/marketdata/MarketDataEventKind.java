package com.penguinsecure.basis.venue.api.marketdata;

/** Wire-level event semantics preserved for the book owner. */
public enum MarketDataEventKind {
    IMAGE,
    SNAPSHOT,
    DELTA,
    RESET
}
