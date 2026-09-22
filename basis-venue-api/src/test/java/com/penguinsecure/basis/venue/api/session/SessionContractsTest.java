package com.penguinsecure.basis.venue.api.session;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class SessionContractsTest {
    @Test
    void enforcesLifecycleAndIncrementsGenerationPerConnection() {
        SessionStateMachine machine = new SessionStateMachine();
        machine.transitionTo(VenueSessionState.CONNECTING);
        assertEquals(1, machine.sessionGeneration());
        machine.transitionTo(VenueSessionState.TLS);
        machine.transitionTo(VenueSessionState.SUBSCRIBING);
        machine.transitionTo(VenueSessionState.LIVE);
        machine.transitionTo(VenueSessionState.DEGRADED);
        machine.transitionTo(VenueSessionState.BACKOFF);
        machine.transitionTo(VenueSessionState.CONNECTING);
        assertEquals(2, machine.sessionGeneration());
        assertThrows(
                IllegalStateException.class, () -> machine.transitionTo(VenueSessionState.LIVE));
    }

    @Test
    void subscriptionSetIsBoundedAndDeduplicated() {
        SubscriptionSet subscriptions = new SubscriptionSet(2);
        assertTrue(subscriptions.add("a"));
        assertTrue(subscriptions.add("a"));
        assertTrue(subscriptions.add("b"));
        assertFalse(subscriptions.add("c"));
        assertEquals(2, subscriptions.size());
        assertTrue(subscriptions.remove("a"));
        assertTrue(subscriptions.add("c"));
        assertThrows(IllegalArgumentException.class, () -> subscriptions.add("x".repeat(257)));
    }

    @Test
    void reconnectDelaySaturatesAndBoundsJitter() {
        ReconnectPolicy policy = new ReconnectPolicy(100, 1_000, 8, 1_000);
        assertEquals(100, policy.delayNanos(0, 0));
        assertEquals(440, policy.delayNanos(2, 1_000));
        assertEquals(900, policy.delayNanos(100, -1_000));
    }
}
