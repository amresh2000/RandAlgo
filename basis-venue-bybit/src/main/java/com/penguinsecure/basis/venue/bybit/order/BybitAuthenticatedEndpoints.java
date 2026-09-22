package com.penguinsecure.basis.venue.bybit.order;

import java.net.URI;

/** Explicit environment endpoints; callers cannot derive or mix them from credentials. */
public record BybitAuthenticatedEndpoints(URI trade, URI privateStream, URI rest) {
    public BybitAuthenticatedEndpoints {
        requireSecure(trade, "wss");
        requireSecure(privateStream, "wss");
        requireSecure(rest, "https");
    }

    public static BybitAuthenticatedEndpoints testnet() {
        return new BybitAuthenticatedEndpoints(
                URI.create("wss://stream-testnet.bybit.com/v5/trade"),
                URI.create("wss://stream-testnet.bybit.com/v5/private"),
                URI.create("https://api-testnet.bybit.com"));
    }

    public static BybitAuthenticatedEndpoints mainnet() {
        return new BybitAuthenticatedEndpoints(
                URI.create("wss://stream.bybit.com/v5/trade"),
                URI.create("wss://stream.bybit.com/v5/private"),
                URI.create("https://api.bybit.com"));
    }

    private static void requireSecure(final URI value, final String scheme) {
        if (value == null
                || !scheme.equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getUserInfo() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("invalid authenticated endpoint");
        }
    }
}
