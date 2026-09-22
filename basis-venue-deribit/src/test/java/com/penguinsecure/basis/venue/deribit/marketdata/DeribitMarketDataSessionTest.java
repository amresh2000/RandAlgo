package com.penguinsecure.basis.venue.deribit.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.api.session.VenueAuthentication;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeribitMarketDataSessionTest {
    @Test
    void authenticatesSubscribesRespondsToTestAndRefreshesWithoutExposingToken() {
        ManualClock clock = new ManualClock();
        FakeConnection connection = new FakeConnection();
        FakeAuthentication authentication = new FakeAuthentication();
        DeribitMarketDataSession session =
                new DeribitMarketDataSession(
                        "BTC-PERPETUAL",
                        "100ms",
                        connection,
                        authentication,
                        new LaneHealthWord(),
                        clock,
                        clock,
                        new ReconnectPolicy(5, 100, 4, 0),
                        8,
                        100);
        session.start();
        session.onTransportReady();
        assertEquals(1, authentication.authentications);
        session.onAuthenticated();
        assertTrue(connection.sent.get(0).contains("set_heartbeat"));
        assertTrue(connection.sent.get(1).contains("book.BTC-PERPETUAL.none.20.100ms"));
        session.onSubscriptionAcknowledged();
        session.onHeartbeatTestRequest();
        assertTrue(connection.sent.getLast().contains("public/test"));
        authentication.refresh = true;
        assertEquals(1, session.doWork());
        assertEquals(1, authentication.refreshes);
        assertEquals(VenueSessionState.LIVE, session.state());
    }

    private static final class ManualClock implements EpochClock, MonotonicClock {
        long nanos;

        @Override
        public long epochNanos() {
            return nanos;
        }

        @Override
        public long nanoTime() {
            return nanos;
        }
    }

    private static final class FakeConnection implements VenueConnectionControl {
        final List<String> sent = new ArrayList<>();

        @Override
        public void connect() {}

        @Override
        public void sendText(CharSequence payload) {
            sent.add(payload.toString());
        }

        @Override
        public void close() {}
    }

    private static final class FakeAuthentication implements VenueAuthentication {
        int authentications;
        int refreshes;
        boolean refresh;

        @Override
        public void authenticate(VenueConnectionControl connection) {
            authentications++;
        }

        @Override
        public boolean refreshRequired(long epochNanos) {
            return refresh;
        }

        @Override
        public void refresh(VenueConnectionControl connection) {
            refreshes++;
            refresh = false;
        }
    }
}
