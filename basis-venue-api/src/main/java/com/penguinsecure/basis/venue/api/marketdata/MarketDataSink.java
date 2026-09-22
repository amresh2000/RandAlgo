package com.penguinsecure.basis.venue.api.marketdata;

/** Non-blocking destination for one fully validated normalized event. */
@FunctionalInterface
public interface MarketDataSink {
    boolean publish(MutableMarketDataEvent event);
}
