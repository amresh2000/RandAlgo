package com.penguinsecure.basis.venue.api.marketdata;

/** Static, bounded normalization profile selected from certified metadata. */
public record MarketDataFeedProfile(
        int profileId,
        int venueId,
        int instrumentId,
        String venueInstrument,
        String venueChannel,
        int priceScale,
        int quantityScale,
        int maximumDepth,
        int maximumFrameBytes,
        boolean completeImageCertified) {
    public MarketDataFeedProfile {
        if (profileId <= 0 || venueId <= 0 || instrumentId <= 0) {
            throw new IllegalArgumentException(
                    "profile, venue, and instrument IDs must be positive");
        }
        if (!isSafeAscii(venueInstrument, 96) || !isSafeAscii(venueChannel, 192)) {
            throw new IllegalArgumentException("invalid venue instrument or channel");
        }
        if (priceScale < 0 || priceScale > 18 || quantityScale < 0 || quantityScale > 18) {
            throw new IllegalArgumentException("decimal scale must be in [0,18]");
        }
        if (maximumDepth <= 0 || maximumDepth > MutableMarketDataEvent.ABSOLUTE_MAX_DEPTH) {
            throw new IllegalArgumentException("invalid maximumDepth");
        }
        if (maximumFrameBytes < 256 || maximumFrameBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("invalid maximumFrameBytes");
        }
    }

    private static boolean isSafeAscii(final String value, final int maximumLength) {
        if (value == null || value.isEmpty() || value.length() > maximumLength) return false;
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (character < 0x21 || character > 0x7e || character == '"' || character == '\\') {
                return false;
            }
        }
        return true;
    }
}
