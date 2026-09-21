package com.penguinsecure.basis.venue.api.session;

/** Explicit session lifecycle; every reconnect enters a new generation. */
public enum VenueSessionState {
    STOPPED,
    CONNECTING,
    TLS,
    AUTHENTICATING,
    SUBSCRIBING,
    LIVE,
    DRAINING,
    DEGRADED,
    BACKOFF
}
