package com.penguinsecure.basis.sim.venue;

/** Explicit venue behavior profile without any wire-adapter dependency. */
public record FakeVenueProfile(
        String name,
        int venueId,
        long writeLatencyNanos,
        long acknowledgementLatencyNanos,
        long fillLatencyNanos,
        long maximumJitterNanos,
        long adverseSlippageTicks) {
    public FakeVenueProfile {
        if (name == null
                || name.isBlank()
                || venueId <= 0
                || writeLatencyNanos < 0
                || acknowledgementLatencyNanos < 0
                || fillLatencyNanos < 0
                || maximumJitterNanos < 0
                || adverseSlippageTicks < 0) {
            throw new IllegalArgumentException("invalid fake venue profile");
        }
    }

    public static FakeVenueProfile bybit(final int venueId) {
        return new FakeVenueProfile("BYBIT", venueId, 100_000, 250_000, 500_000, 50_000, 1);
    }

    public static FakeVenueProfile deribit(final int venueId) {
        return new FakeVenueProfile("DERIBIT", venueId, 80_000, 200_000, 400_000, 40_000, 1);
    }
}
