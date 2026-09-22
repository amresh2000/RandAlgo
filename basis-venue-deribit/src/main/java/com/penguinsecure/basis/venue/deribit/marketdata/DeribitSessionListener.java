package com.penguinsecure.basis.venue.deribit.marketdata;

/** Receives allocation-free transport control events from the Deribit Netty handler. */
public interface DeribitSessionListener {
    DeribitSessionListener NOOP = new DeribitSessionListener() {};

    default void onTransportReady() {}

    default void onServerActivity() {}

    default void onAuthenticated() {}

    default void onSubscriptionAcknowledged() {}

    default void onHeartbeatTestRequest() {}

    default boolean onDisconnected() {
        return false;
    }
}
