package com.penguinsecure.basis.venue.bybit.marketdata;

/** Receives allocation-free transport control events from the Bybit Netty handler. */
public interface BybitSessionListener {
    BybitSessionListener NOOP = new BybitSessionListener() {};

    default void onTransportReady() {}

    default void onServerActivity() {}

    default void onSubscriptionAcknowledged() {}

    default boolean onDisconnected() {
        return false;
    }
}
