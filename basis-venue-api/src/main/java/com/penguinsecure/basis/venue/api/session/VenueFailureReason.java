package com.penguinsecure.basis.venue.api.session;

/** Stable health reasons consumed without log parsing. */
public enum VenueFailureReason {
    NONE,
    DISCONNECTED,
    HEARTBEAT_TIMEOUT,
    AUTHENTICATION_FAILED,
    SUBSCRIPTION_FAILED,
    MALFORMED_INPUT,
    OVERSIZE_FRAME,
    RING_OVERFLOW,
    UNSUPPORTED_PROFILE,
    CAPABILITY_NOT_CERTIFIED
}
