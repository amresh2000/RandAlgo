package com.penguinsecure.basis.venue.deribit.order;

/** Certified wire profile for one Deribit order instrument. */
public record DeribitOrderProfile(
        int venueId,
        int instrumentId,
        String instrument,
        String currency,
        int amountScale,
        int priceScale) {
    public DeribitOrderProfile {
        if (venueId <= 0
                || venueId > 0xffff
                || instrumentId <= 0
                || !safe(instrument, 96, true)
                || !safe(currency, 12, false)
                || amountScale < 0
                || amountScale > 18
                || priceScale < 0
                || priceScale > 18)
            throw new IllegalArgumentException("invalid Deribit order profile");
    }

    public static DeribitOrderProfile inverseBtcPerpetual(
            final int venueId, final int instrumentId) {
        return new DeribitOrderProfile(venueId, instrumentId, "BTC-PERPETUAL", "BTC", 0, 2);
    }

    private static boolean safe(final String value, final int maximum, final boolean dash) {
        if (value == null || value.isEmpty() || value.length() > maximum) return false;
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (!((character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || (dash && character == '-'))) return false;
        }
        return true;
    }
}
