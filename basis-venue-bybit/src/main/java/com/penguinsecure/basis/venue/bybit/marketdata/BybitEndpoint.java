package com.penguinsecure.basis.venue.bybit.marketdata;

import com.penguinsecure.basis.core.product.ProductFamily;
import java.net.URI;

/** Product-family-safe public WebSocket endpoint selection. */
public final class BybitEndpoint {
    private static final URI LINEAR = URI.create("wss://stream.bybit.com/v5/public/linear");
    private static final URI INVERSE = URI.create("wss://stream.bybit.com/v5/public/inverse");

    private BybitEndpoint() {}

    public static URI forProduct(final ProductFamily family) {
        if (family == null) throw new NullPointerException("family is required");
        return switch (family) {
            case LINEAR_PERPETUAL, LINEAR_FUTURE -> LINEAR;
            case INVERSE_PERPETUAL, INVERSE_FUTURE -> INVERSE;
        };
    }
}
