package com.penguinsecure.basis.venue.deribit.order;

import java.net.URI;

/** Explicit Deribit authenticated WebSocket and HTTP environments. */
public record DeribitAuthenticatedEndpoints(URI webSocket, URI http) {
    public DeribitAuthenticatedEndpoints {
        if (webSocket == null
                || http == null
                || !"wss".equalsIgnoreCase(webSocket.getScheme())
                || !"https".equalsIgnoreCase(http.getScheme()))
            throw new IllegalArgumentException("secure endpoints are required");
    }

    public static DeribitAuthenticatedEndpoints testnet() {
        return new DeribitAuthenticatedEndpoints(
                URI.create("wss://test.deribit.com/ws/api/v2"),
                URI.create("https://test.deribit.com/api/v2/"));
    }

    public static DeribitAuthenticatedEndpoints mainnet() {
        return new DeribitAuthenticatedEndpoints(
                URI.create("wss://www.deribit.com/ws/api/v2"),
                URI.create("https://www.deribit.com/api/v2/"));
    }
}
