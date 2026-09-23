package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.api.session.SessionStateMachine;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;
import com.penguinsecure.basis.venue.deribit.marketdata.DeribitSessionListener;

/** Independent authenticated Deribit order or private-stream session owner. */
public final class DeribitAuthenticatedSession implements DeribitSessionListener, AutoCloseable {
    public enum Role {
        ORDER,
        PRIVATE
    }

    private static final long TOKEN_REFRESH_MARGIN_NANOS = 30_000_000_000L;
    private final Role role;
    private final VenueConnectionControl connection;
    private final DeribitCredentials credentials;
    private final DeribitTokenState tokens;
    private final DeribitOrderRequestEncoder encoder;
    private final DeribitRequestIdSequence requestIds;
    private final DeribitNonceSource nonceSource;
    private final LaneHealthWord health;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final ReconnectPolicy reconnectPolicy;
    private final long producerEpoch, activityTimeoutNanos;
    private final int heartbeatSeconds;
    private final SessionStateMachine lifecycle = new SessionStateMachine();
    private final StringBuilder nonce = new StringBuilder(48);
    private long authRequestId,
            heartbeatRequestId,
            subscriptionRequestId,
            refreshRequestId,
            lastActivityNanos,
            reconnectAtNanos;
    private int reconnectAttempt;

    @SuppressWarnings("ParameterNumber")
    public DeribitAuthenticatedSession(
            final Role role,
            final VenueConnectionControl connection,
            final DeribitCredentials credentials,
            final DeribitTokenState tokens,
            final DeribitOrderRequestEncoder encoder,
            final DeribitRequestIdSequence requestIds,
            final DeribitNonceSource nonceSource,
            final LaneHealthWord health,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock,
            final ReconnectPolicy reconnectPolicy,
            final long producerEpoch,
            final int heartbeatSeconds,
            final long activityTimeoutNanos) {
        if (role == null
                || connection == null
                || credentials == null
                || tokens == null
                || encoder == null
                || requestIds == null
                || nonceSource == null
                || health == null
                || epochClock == null
                || monotonicClock == null
                || reconnectPolicy == null)
            throw new NullPointerException("dependencies are required");
        if (heartbeatSeconds < 10 || activityTimeoutNanos <= heartbeatSeconds * 1_000_000_000L)
            throw new IllegalArgumentException("invalid heartbeat bounds");
        this.role = role;
        this.connection = connection;
        this.credentials = credentials;
        this.tokens = tokens;
        this.encoder = encoder;
        this.requestIds = requestIds;
        this.nonceSource = nonceSource;
        this.health = health;
        this.epochClock = epochClock;
        this.monotonicClock = monotonicClock;
        this.reconnectPolicy = reconnectPolicy;
        this.producerEpoch = producerEpoch;
        this.heartbeatSeconds = heartbeatSeconds;
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
        authRequestId = requestIds.next();
        nonceSource.next(nonce);
        connection.sendText(
                encoder.authentication(
                        authRequestId, epochClock.epochNanos() / 1_000_000L, nonce, credentials));
        erase(nonce);
        encoder.clearSensitiveOutput();
    }

    /** Consumes session-owned response IDs; returns false for order-command correlation. */
    public boolean onResponse(final MutableDeribitResponse response) {
        onServerActivity();
        if (response.heartbeatTest()) {
            answerTest();
            return true;
        }
        final long id = response.requestId();
        if (id == authRequestId || id == refreshRequestId) {
            final boolean initial = id == authRequestId;
            if (response.errorCode() != 0
                    || !response.hasTokens()
                    || !tokens.replace(
                            response.accessToken(),
                            response.accessLength(),
                            response.refreshToken(),
                            response.refreshLength(),
                            response.expiresInSeconds(),
                            epochClock.epochNanos(),
                            TOKEN_REFRESH_MARGIN_NANOS)) {
                fail(VenueFailureReason.AUTHENTICATION_FAILED, monotonicClock.nanoTime());
                return true;
            }
            if (initial) {
                lifecycle.transitionTo(VenueSessionState.SUBSCRIBING);
                heartbeatRequestId = requestIds.next();
                connection.sendText(encoder.heartbeat(heartbeatRequestId, heartbeatSeconds));
            }
            authRequestId = 0;
            refreshRequestId = 0;
            return true;
        }
        if (id == heartbeatRequestId) {
            if (response.errorCode() != 0)
                fail(VenueFailureReason.SUBSCRIPTION_FAILED, monotonicClock.nanoTime());
            else if (role == Role.PRIVATE) {
                subscriptionRequestId = requestIds.next();
                connection.sendText(encoder.privateSubscription(subscriptionRequestId));
            } else live();
            heartbeatRequestId = 0;
            return true;
        }
        if (id == subscriptionRequestId) {
            if (response.errorCode() != 0)
                fail(VenueFailureReason.SUBSCRIPTION_FAILED, monotonicClock.nanoTime());
            else live();
            subscriptionRequestId = 0;
            return true;
        }
        return false;
    }

    @Override
    public void onServerActivity() {
        lastActivityNanos = monotonicClock.nanoTime();
    }

    @Override
    public void onHeartbeatTestRequest() {
        answerTest();
    }

    public int doWork() {
        final long now = monotonicClock.nanoTime();
        if (lifecycle.state() == VenueSessionState.LIVE) {
            if (now - lastActivityNanos >= activityTimeoutNanos) {
                fail(VenueFailureReason.HEARTBEAT_TIMEOUT, now);
                return 1;
            }
            if (refreshRequestId == 0 && tokens.refreshRequired(epochClock.epochNanos())) {
                refreshRequestId = requestIds.next();
                try {
                    connection.sendText(encoder.refresh(refreshRequestId, tokens));
                } finally {
                    encoder.clearSensitiveOutput();
                }
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
                && lifecycle.state() != VenueSessionState.BACKOFF)
            fail(VenueFailureReason.DISCONNECTED, monotonicClock.nanoTime());
        return true;
    }

    public VenueSessionState state() {
        return lifecycle.state();
    }

    public long sessionGeneration() {
        return lifecycle.sessionGeneration();
    }

    private void answerTest() {
        if (lifecycle.state() == VenueSessionState.LIVE
                || lifecycle.state() == VenueSessionState.SUBSCRIBING)
            connection.sendText(encoder.test(requestIds.next()));
    }

    private void live() {
        lifecycle.transitionTo(VenueSessionState.LIVE);
        reconnectAttempt = 0;
        lastActivityNanos = monotonicClock.nanoTime();
        health.publish(
                LaneHealthState.HEALTHY,
                VenueFailureReason.NONE,
                producerEpoch,
                lifecycle.sessionGeneration(),
                0);
    }

    private void fail(final VenueFailureReason reason, final long now) {
        if (lifecycle.state() != VenueSessionState.DEGRADED)
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
        tokens.close();
        erase(nonce);
        encoder.clearSensitiveOutput();
    }

    private static void erase(final StringBuilder value) {
        for (int i = 0; i < value.length(); i++) value.setCharAt(i, '\0');
        value.setLength(0);
    }
}
