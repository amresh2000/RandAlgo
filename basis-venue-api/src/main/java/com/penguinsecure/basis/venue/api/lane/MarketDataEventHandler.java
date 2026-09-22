package com.penguinsecure.basis.venue.api.lane;

import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;

/** Core-side callback. The mutable event is reused and must not escape the call. */
@FunctionalInterface
public interface MarketDataEventHandler {
    void onMarketData(MutableMarketDataEvent event);
}
