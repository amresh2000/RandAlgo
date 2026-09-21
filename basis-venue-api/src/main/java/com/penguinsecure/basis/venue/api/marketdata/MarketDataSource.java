package com.penguinsecure.basis.venue.api.marketdata;

/** Lifecycle boundary for a single-producer venue feed. */
public interface MarketDataSource extends AutoCloseable {
    void start();

    void stop();

    long sessionGeneration();

    @Override
    void close();
}
