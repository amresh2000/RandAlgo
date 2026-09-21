package com.penguinsecure.basis.venue.bybit.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.venue.api.lane.LaneHealthSnapshot;
import com.penguinsecure.basis.venue.api.lane.LaneHealthState;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitMarketDataSessionTest {
    @Test
    void subscribesHeartbeatsTimesOutAndReconnectsWithNewGeneration() {
        ManualNanoClock clock = new ManualNanoClock();
        FakeConnection connection = new FakeConnection();
        LaneHealthWord health = new LaneHealthWord();
        BybitMarketDataSession session =
                new BybitMarketDataSession(
                        "BTCUSDT",
                        50,
                        connection,
                        health,
                        clock,
                        new ReconnectPolicy(5, 100, 4, 0),
                        7,
                        20,
                        50);
        connection.onClose = session::onDisconnected;
        session.start();
        assertEquals(1, connection.connects);
        session.onTransportReady();
        assertTrue(connection.sent.getFirst().contains("orderbook.50.BTCUSDT"));
        session.onSubscriptionAcknowledged();
        assertEquals(VenueSessionState.LIVE, session.state());
        clock.nanos = 20;
        assertEquals(1, session.doWork());
        assertEquals("{\"op\":\"ping\"}", connection.sent.getLast());
        clock.nanos = 51;
        assertEquals(1, session.doWork());
        assertEquals(VenueSessionState.BACKOFF, session.state());
        LaneHealthSnapshot snapshot = new LaneHealthSnapshot();
        health.read(snapshot);
        assertEquals(LaneHealthState.DEGRADED, snapshot.state());
        assertEquals(
                com.penguinsecure.basis.venue.api.session.VenueFailureReason.HEARTBEAT_TIMEOUT,
                snapshot.reason());
        clock.nanos = 56;
        assertEquals(1, session.doWork());
        assertEquals(2, connection.connects);
        assertEquals(2, session.sessionGeneration());
    }

    private static final class ManualNanoClock
            implements com.penguinsecure.basis.core.time.MonotonicClock {
        long nanos;

        @Override
        public long nanoTime() {
            return nanos;
        }
    }

    private static final class FakeConnection implements VenueConnectionControl {
        int connects;
        Runnable onClose = () -> {};
        final List<String> sent = new ArrayList<>();

        @Override
        public void connect() {
            connects++;
        }

        @Override
        public void sendText(CharSequence payload) {
            sent.add(payload.toString());
        }

        @Override
        public void close() {
            onClose.run();
        }
    }
}
