package com.penguinsecure.basis.venue.api.session;

/** Single-thread-owned guard for legal venue lifecycle transitions. */
public final class SessionStateMachine {
    private VenueSessionState state = VenueSessionState.STOPPED;
    private long sessionGeneration;

    public VenueSessionState state() {
        return state;
    }

    public long sessionGeneration() {
        return sessionGeneration;
    }

    public void transitionTo(final VenueSessionState next) {
        if (!isLegal(state, next)) {
            throw new IllegalStateException("illegal session transition " + state + " -> " + next);
        }
        if (state == VenueSessionState.BACKOFF && next == VenueSessionState.CONNECTING) {
            sessionGeneration++;
        } else if (state == VenueSessionState.STOPPED && next == VenueSessionState.CONNECTING) {
            sessionGeneration++;
        }
        state = next;
    }

    private static boolean isLegal(final VenueSessionState from, final VenueSessionState to) {
        if (to == VenueSessionState.STOPPED) return from != VenueSessionState.STOPPED;
        return switch (from) {
            case STOPPED, BACKOFF -> to == VenueSessionState.CONNECTING;
            case CONNECTING -> to == VenueSessionState.TLS || to == VenueSessionState.DEGRADED;
            case TLS ->
                    to == VenueSessionState.AUTHENTICATING
                            || to == VenueSessionState.SUBSCRIBING
                            || to == VenueSessionState.DEGRADED;
            case AUTHENTICATING ->
                    to == VenueSessionState.SUBSCRIBING || to == VenueSessionState.DEGRADED;
            case SUBSCRIBING -> to == VenueSessionState.LIVE || to == VenueSessionState.DEGRADED;
            case LIVE -> to == VenueSessionState.DRAINING || to == VenueSessionState.DEGRADED;
            case DRAINING -> to == VenueSessionState.DEGRADED || to == VenueSessionState.BACKOFF;
            case DEGRADED -> to == VenueSessionState.BACKOFF;
        };
    }
}
