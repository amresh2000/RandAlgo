package com.penguinsecure.basis.venue.bybit.marketdata;

import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataSource;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.api.session.SessionStateMachine;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;

/** Bybit heartbeat/subscription/reconnect coordinator around one Netty connection. */
public final class BybitMarketDataSession implements MarketDataSource, BybitSessionListener {
    private static final String PING = "{\"op\":\"ping\"}";

    private final VenueConnectionControl connection;
    private final LaneHealthWord healthWord;
    private final MonotonicClock clock;
    private final ReconnectPolicy reconnectPolicy;
    private final long producerEpoch;
    private final long heartbeatIntervalNanos;
    private final long activityTimeoutNanos;
    private final String subscription;
    private final SessionStateMachine lifecycle = new SessionStateMachine();
    private long lastActivityNanos;
    private long nextHeartbeatNanos;
    private long reconnectAtNanos;
    private int reconnectAttempt;

    public BybitMarketDataSession(
            final String symbol,
            final int depth,
            final VenueConnectionControl connection,
            final LaneHealthWord healthWord,
            final MonotonicClock clock,
            final ReconnectPolicy reconnectPolicy,
            final long producerEpoch,
            final long heartbeatIntervalNanos,
            final long activityTimeoutNanos) {
        if (!isSafeSymbol(symbol) || depth <= 0)
            throw new IllegalArgumentException("invalid symbol or depth");
        if (connection == null || healthWord == null || clock == null || reconnectPolicy == null) {
            throw new NullPointerException("session dependencies are required");
        }
        if (heartbeatIntervalNanos <= 0 || activityTimeoutNanos <= heartbeatIntervalNanos) {
            throw new IllegalArgumentException("invalid heartbeat bounds");
        }
        this.connection = connection;
        this.healthWord = healthWord;
        this.clock = clock;
        this.reconnectPolicy = reconnectPolicy;
        this.producerEpoch = producerEpoch;
        this.heartbeatIntervalNanos = heartbeatIntervalNanos;
        this.activityTimeoutNanos = activityTimeoutNanos;
        subscription =
                "{\"op\":\"subscribe\",\"args\":[\"orderbook." + depth + "." + symbol + "\"]}";
    }

    @Override
    public void start() {
        if (lifecycle.state() != VenueSessionState.STOPPED) return;
        lifecycle.transitionTo(VenueSessionState.CONNECTING);
        connection.connect();
    }

    @Override
    public void onTransportReady() {
        lifecycle.transitionTo(VenueSessionState.TLS);
        lifecycle.transitionTo(VenueSessionState.SUBSCRIBING);
        connection.sendText(subscription);
    }

    @Override
    public void onSubscriptionAcknowledged() {
        if (lifecycle.state() != VenueSessionState.SUBSCRIBING) return;
        lifecycle.transitionTo(VenueSessionState.LIVE);
        reconnectAttempt = 0;
        final long now = clock.nanoTime();
        lastActivityNanos = now;
        nextHeartbeatNanos = now + heartbeatIntervalNanos;
        healthWord.publish(
                LaneHealthState.HEALTHY,
                VenueFailureReason.NONE,
                producerEpoch,
                lifecycle.sessionGeneration(),
                0);
    }

    @Override
    public void onServerActivity() {
        lastActivityNanos = clock.nanoTime();
    }

    public int doWork() {
        final long now = clock.nanoTime();
        if (lifecycle.state() == VenueSessionState.LIVE) {
            if (now - lastActivityNanos >= activityTimeoutNanos) {
                fail(VenueFailureReason.HEARTBEAT_TIMEOUT, now);
                return 1;
            }
            if (now - nextHeartbeatNanos >= 0) {
                connection.sendText(PING);
                nextHeartbeatNanos = now + heartbeatIntervalNanos;
                return 1;
            }
        } else if (lifecycle.state() == VenueSessionState.BACKOFF && now - reconnectAtNanos >= 0) {
            lifecycle.transitionTo(VenueSessionState.CONNECTING);
            connection.connect();
            return 1;
        }
        return 0;
    }

    @Override
    public boolean onDisconnected() {
        if (lifecycle.state() != VenueSessionState.STOPPED
                && lifecycle.state() != VenueSessionState.BACKOFF) {
            fail(VenueFailureReason.DISCONNECTED, clock.nanoTime());
        }
        return true;
    }

    private void fail(final VenueFailureReason reason, final long now) {
        lifecycle.transitionTo(VenueSessionState.DEGRADED);
        lifecycle.transitionTo(VenueSessionState.BACKOFF);
        healthWord.publish(
                LaneHealthState.DEGRADED, reason, producerEpoch, lifecycle.sessionGeneration(), 0);
        reconnectAtNanos = now + reconnectPolicy.delayNanos(reconnectAttempt++, 0);
        connection.close();
    }

    @Override
    public void stop() {
        if (lifecycle.state() == VenueSessionState.STOPPED) return;
        connection.close();
        lifecycle.transitionTo(VenueSessionState.STOPPED);
        healthWord.publish(
                LaneHealthState.STOPPED,
                VenueFailureReason.NONE,
                producerEpoch,
                lifecycle.sessionGeneration(),
                0);
    }

    @Override
    public long sessionGeneration() {
        return lifecycle.sessionGeneration();
    }

    public VenueSessionState state() {
        return lifecycle.state();
    }

    @Override
    public void close() {
        stop();
    }

    private static boolean isSafeSymbol(final String symbol) {
        if (symbol == null || symbol.isEmpty() || symbol.length() > 64) return false;
        for (int i = 0; i < symbol.length(); i++) {
            final char value = symbol.charAt(i);
            if (!((value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9') || value == '-'))
                return false;
        }
        return true;
    }
}
