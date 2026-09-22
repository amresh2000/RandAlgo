package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.api.session.SessionStateMachine;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;
import com.penguinsecure.basis.venue.bybit.marketdata.BybitSessionListener;

/** Single-owner authenticated trade or private-stream session coordinator. */
public final class BybitAuthenticatedSession implements BybitSessionListener, AutoCloseable {
    public enum Role {
        TRADE,
        PRIVATE
    }

    private static final long AUTH_EXPIRY_MILLIS = 10_000;
    private final Role role;
    private final VenueConnectionControl connection;
    private final BybitCredentials credentials;
    private final BybitOrderRequestEncoder encoder;
    private final LaneHealthWord health;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final ReconnectPolicy reconnectPolicy;
    private final long producerEpoch;
    private final long heartbeatIntervalNanos;
    private final long activityTimeoutNanos;
    private final SessionStateMachine lifecycle = new SessionStateMachine();
    private long lastActivityNanos;
    private long nextHeartbeatNanos;
    private long reconnectAtNanos;
    private int reconnectAttempt;

    @SuppressWarnings("ParameterNumber")
    public BybitAuthenticatedSession(
            final Role role,
            final VenueConnectionControl connection,
            final BybitCredentials credentials,
            final BybitOrderRequestEncoder encoder,
            final LaneHealthWord health,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock,
            final ReconnectPolicy reconnectPolicy,
            final long producerEpoch,
            final long heartbeatIntervalNanos,
            final long activityTimeoutNanos) {
        if (role == null
                || connection == null
                || credentials == null
                || encoder == null
                || health == null
                || epochClock == null
                || monotonicClock == null
                || reconnectPolicy == null)
            throw new NullPointerException("session dependencies are required");
        if (heartbeatIntervalNanos <= 0 || activityTimeoutNanos <= heartbeatIntervalNanos) {
            throw new IllegalArgumentException("invalid heartbeat bounds");
        }
        this.role = role;
        this.connection = connection;
        this.credentials = credentials;
        this.encoder = encoder;
        this.health = health;
        this.epochClock = epochClock;
        this.monotonicClock = monotonicClock;
        this.reconnectPolicy = reconnectPolicy;
        this.producerEpoch = producerEpoch;
        this.heartbeatIntervalNanos = heartbeatIntervalNanos;
        this.activityTimeoutNanos = activityTimeoutNanos;
    }

    public void start() {
        if (lifecycle.state() != VenueSessionState.STOPPED) return;
        lifecycle.transitionTo(VenueSessionState.CONNECTING);
        connection.connect();
    }

    @Override
    public void onTransportReady() {
        lifecycle.transitionTo(VenueSessionState.TLS);
        lifecycle.transitionTo(VenueSessionState.AUTHENTICATING);
        final long expires = epochClock.epochNanos() / 1_000_000L + AUTH_EXPIRY_MILLIS;
        connection.sendText(encoder.authentication(credentials, expires));
    }

    public void onAuthenticationResult(final boolean success) {
        if (lifecycle.state() != VenueSessionState.AUTHENTICATING) return;
        if (!success) {
            fail(VenueFailureReason.AUTHENTICATION_FAILED, monotonicClock.nanoTime());
            return;
        }
        lifecycle.transitionTo(VenueSessionState.SUBSCRIBING);
        if (role == Role.PRIVATE) connection.sendText(encoder.privateSubscription());
        else live();
    }

    @Override
    public void onSubscriptionAcknowledged() {
        if (role == Role.PRIVATE && lifecycle.state() == VenueSessionState.SUBSCRIBING) live();
    }

    @Override
    public void onServerActivity() {
        lastActivityNanos = monotonicClock.nanoTime();
    }

    public int doWork() {
        final long now = monotonicClock.nanoTime();
        if (lifecycle.state() == VenueSessionState.LIVE) {
            if (now - lastActivityNanos >= activityTimeoutNanos) {
                fail(VenueFailureReason.HEARTBEAT_TIMEOUT, now);
                return 1;
            }
            if (now - nextHeartbeatNanos >= 0) {
                connection.sendText(encoder.ping());
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
            fail(VenueFailureReason.DISCONNECTED, monotonicClock.nanoTime());
        }
        return true;
    }

    public VenueSessionState state() {
        return lifecycle.state();
    }

    public long sessionGeneration() {
        return lifecycle.sessionGeneration();
    }

    private void live() {
        lifecycle.transitionTo(VenueSessionState.LIVE);
        reconnectAttempt = 0;
        final long now = monotonicClock.nanoTime();
        lastActivityNanos = now;
        nextHeartbeatNanos = now + heartbeatIntervalNanos;
        health.publish(
                LaneHealthState.HEALTHY,
                VenueFailureReason.NONE,
                producerEpoch,
                lifecycle.sessionGeneration(),
                0);
    }

    private void fail(final VenueFailureReason reason, final long now) {
        lifecycle.transitionTo(VenueSessionState.DEGRADED);
        lifecycle.transitionTo(VenueSessionState.BACKOFF);
        health.publish(
                LaneHealthState.DEGRADED, reason, producerEpoch, lifecycle.sessionGeneration(), 0);
        reconnectAtNanos = now + reconnectPolicy.delayNanos(reconnectAttempt++, 0);
        connection.close();
    }

    @Override
    public void close() {
        if (lifecycle.state() != VenueSessionState.STOPPED) {
            connection.close();
            lifecycle.transitionTo(VenueSessionState.STOPPED);
            health.publish(
                    LaneHealthState.STOPPED,
                    VenueFailureReason.NONE,
                    producerEpoch,
                    lifecycle.sessionGeneration(),
                    0);
        }
        credentials.close();
    }
}
