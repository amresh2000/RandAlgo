package com.penguinsecure.basis.venue.bybit.order;

/** Certified wire mapping for one Bybit order route. */
public record BybitOrderProfile(
        int venueId,
        int instrumentId,
        String category,
        String symbol,
        int priceScale,
        int quantityScale,
        long receiveWindowMillis) {
    public BybitOrderProfile {
        if (venueId <= 0
                || instrumentId <= 0
                || !safe(category, false)
                || !safe(symbol, true)
                || priceScale < 0
                || priceScale > 18
                || quantityScale < 0
                || quantityScale > 18
                || receiveWindowMillis <= 0
                || receiveWindowMillis > 10_000) {
            throw new IllegalArgumentException("invalid Bybit order profile");
        }
    }

    public static BybitOrderProfile inverseBtcUsd(final int venueId, final int instrumentId) {
        return new BybitOrderProfile(venueId, instrumentId, "inverse", "BTCUSD", 2, 0, 5_000);
    }

    private static boolean safe(final String value, final boolean uppercase) {
        if (value == null || value.isEmpty() || value.length() > 32) return false;
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            final boolean letter =
                    uppercase
                            ? character >= 'A' && character <= 'Z'
                            : character >= 'a' && character <= 'z';
            if (!(letter || character >= '0' && character <= '9' || character == '-')) return false;
        }
        return true;
    }
}
