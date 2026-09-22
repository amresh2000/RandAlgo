package com.penguinsecure.basis.app.marketdata;

import com.penguinsecure.basis.venue.api.marketdata.MarketDataSink;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;

/** Observation wrapper exposing publication attempts without retaining mutable events. */
final class CountingMarketDataSink implements MarketDataSink {
    private final MarketDataSink delegate;
    private volatile long attempts;
    private volatile long published;
    private volatile long lastSessionGeneration;
    private volatile boolean lastComplete;

    CountingMarketDataSink(final MarketDataSink delegate) {
        if (delegate == null) throw new NullPointerException("delegate is required");
        this.delegate = delegate;
    }

    @Override
    public boolean publish(final MutableMarketDataEvent event) {
        attempts++;
        lastSessionGeneration = event.sessionGeneration();
        lastComplete = event.isComplete();
        final boolean accepted = delegate.publish(event);
        if (accepted) published++;
        return accepted;
    }

    long attempts() {
        return attempts;
    }

    long published() {
        return published;
    }

    long lastSessionGeneration() {
        return lastSessionGeneration;
    }

    boolean lastComplete() {
        return lastComplete;
    }
}
