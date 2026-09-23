package com.penguinsecure.basis.venue.deribit.order;

/** Single-owner accounting for Deribit too-many-requests responses. */
public final class DeribitRateLimitState {
    private long rejected;
    private int lastCode;

    public void rejected(final int errorCode) {
        rejected++;
        lastCode = errorCode;
    }

    public long rejected() {
        return rejected;
    }

    public int lastCode() {
        return lastCode;
    }
}
