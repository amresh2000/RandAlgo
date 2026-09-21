package com.penguinsecure.basis.venue.deribit.marketdata;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataSource;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.api.session.SessionStateMachine;
import com.penguinsecure.basis.venue.api.session.VenueAuthentication;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;

/** Deribit authentication/token/heartbeat/subscription/reconnect coordinator. */
public final class DeribitMarketDataSession implements MarketDataSource, DeribitSessionListener {
    private static final String TEST_RESPONSE =
            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"public/test\",\"params\":{}}";
    private static final String HEARTBEAT =
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"public/set_heartbeat\",\"params\":{\"interval\":10}}";

    private final VenueConnectionControl connection;
    private final VenueAuthentication authentication;
    private final LaneHealthWord healthWord;
    private final EpochClock epochClock;
    private final MonotonicClock clock;
    private final ReconnectPolicy reconnectPolicy;
    private final long producerEpoch;
    private final long activityTimeoutNanos;
    private final String subscription;
    private final SessionStateMachine lifecycle = new SessionStateMachine();
    private long lastActivityNanos;
    private long reconnectAtNanos;
    private int reconnectAttempt;

    public DeribitMarketDataSession(
            final String instrument,
            final String interval,
            final VenueConnectionControl connection,
            final VenueAuthentication authentication,
            final LaneHealthWord healthWord,
            final EpochClock epochClock,
            final MonotonicClock clock,
            final ReconnectPolicy reconnectPolicy,
            final long producerEpoch,
            final long activityTimeoutNanos) {
        if (!isSafeName(instrument)
                || !("100ms".equals(interval)
                        || "raw".equals(interval)
                        || "agg2".equals(interval))) {
            throw new IllegalArgumentException("invalid instrument or interval");
        }
        if (connection == null
                || authentication == null
                || healthWord == null
                || epochClock == null
                || clock == null
                || reconnectPolicy == null)
            throw new NullPointerException("dependencies are required");
        if (activityTimeoutNanos <= 0)
            throw new IllegalArgumentException("activity timeout must be positive");
        this.connection = connection;
        this.authentication = authentication;
        this.healthWord = healthWord;
        this.epochClock = epochClock;
        this.clock = clock;
        this.reconnectPolicy = reconnectPolicy;
        this.producerEpoch = producerEpoch;
        this.activityTimeoutNanos = activityTimeoutNanos;
        subscription =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"public/subscribe\",\"params\":{\"channels\":[\"book."
                        + instrument
                        + ".none.20."
                        + interval
                        + "\"]}}";
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
        lifecycle.transitionTo(VenueSessionState.AUTHENTICATING);
        authentication.authenticate(connection);
    }

    @Override
    public void onAuthenticated() {
        if (lifecycle.state() != VenueSessionState.AUTHENTICATING) return;
        lifecycle.transitionTo(VenueSessionState.SUBSCRIBING);
        connection.sendText(HEARTBEAT);
        connection.sendText(subscription);
    }

    @Override
    public void onSubscriptionAcknowledged() {
        if (lifecycle.state() != VenueSessionState.SUBSCRIBING) return;
        lifecycle.transitionTo(VenueSessionState.LIVE);
        reconnectAttempt = 0;
        lastActivityNanos = clock.nanoTime();
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

    @Override
    public void onHeartbeatTestRequest() {
        connection.sendText(TEST_RESPONSE);
        onServerActivity();
    }

    public int doWork() {
        final long now = clock.nanoTime();
        if (lifecycle.state() == VenueSessionState.LIVE) {
            if (now - lastActivityNanos >= activityTimeoutNanos) {
                fail(VenueFailureReason.HEARTBEAT_TIMEOUT, now);
                return 1;
            }
            if (authentication.refreshRequired(epochClock.epochNanos())) {
                authentication.refresh(connection);
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

    private static boolean isSafeName(final String value) {
        if (value == null || value.isEmpty() || value.length() > 96) return false;
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (!((character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '-')) return false;
        }
        return true;
    }
}
