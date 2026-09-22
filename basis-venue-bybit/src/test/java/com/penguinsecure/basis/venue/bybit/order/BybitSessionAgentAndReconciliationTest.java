package com.penguinsecure.basis.venue.bybit.order;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.OrderUrgency;
import com.penguinsecure.basis.core.identity.LocalOrderIdCodec;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.oems.fact.OrderFactType;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.lane.OrderFactLane;
import com.penguinsecure.basis.venue.api.order.OrderFactLaneSink;
import com.penguinsecure.basis.venue.api.order.VenueOrderState;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BybitSessionAgentAndReconciliationTest {
    @Test
    void authenticatesWritesCorrelatesAndMarksOutstandingRequestAmbiguousOnDisconnect() {
        TestClock clock = new TestClock(1_000_000_000L);
        FakeConnection connection = new FakeConnection();
        BybitOrderProfile profile = BybitOrderProfile.inverseBtcUsd(7, 11);
        BybitOrderRequestEncoder encoder = new BybitOrderRequestEncoder(profile);
        LaneHealthWord health = new LaneHealthWord();
        BybitAuthenticatedSession session =
                new BybitAuthenticatedSession(
                        BybitAuthenticatedSession.Role.TRADE,
                        connection,
                        credentials(),
                        encoder,
                        health,
                        clock,
                        clock,
                        new ReconnectPolicy(5, 100, 4, 0),
                        1,
                        20,
                        50);
        session.start();
        session.onTransportReady();
        assertTrue(connection.sent.getFirst().contains("\"op\":\"auth\""));
        session.onAuthenticationResult(true);
        assertEquals(VenueSessionState.LIVE, session.state());

        OrderFactLane facts = new OrderFactLane(4096, health, clock, 1);
        BybitRequestCorrelationTable correlations = new BybitRequestCorrelationTable(4);
        BybitActiveOrderTable activeOrders = new BybitActiveOrderTable(4);
        BybitOrderAgent agent =
                new BybitOrderAgent(
                        profile,
                        session,
                        connection,
                        encoder,
                        correlations,
                        activeOrders,
                        new BybitRateLimitState(),
                        new OrderFactLaneSink(facts),
                        clock,
                        clock);
        MutableOrderCommand first = command(1, session.sessionGeneration());
        agent.onCommand(first);
        assertEquals(1, correlations.size());
        assertTrue(connection.sent.getLast().contains("order.create"));

        MutableBybitTradeResponse accepted = new MutableBybitTradeResponse();
        accepted.set(
                BybitTradeResponseKind.COMMAND_ACCEPTED,
                0,
                first.localOrderIdHigh(),
                first.localOrderIdLow(),
                10,
                9,
                1000);
        assertEquals(BybitOrderParseStatus.OK, agent.onResponse(accepted));
        assertEquals(0, correlations.size());

        agent.onCommand(command(2, session.sessionGeneration()));
        assertEquals(1, agent.onDisconnected());
        List<OrderFactType> types = new ArrayList<>();
        facts.drain(fact -> types.add(fact.type()), 10);
        assertEquals(
                List.of(
                        OrderFactType.WRITE_ACCEPTED,
                        OrderFactType.WRITE_ACCEPTED,
                        OrderFactType.WRITE_AMBIGUOUS),
                types);
        assertEquals(1, agent.onPrivateDisconnected());
        types.clear();
        facts.drain(fact -> types.add(fact.type()), 10);
        assertEquals(List.of(OrderFactType.DISCONNECTED), types);
        session.close();
    }

    @Test
    void consumesEveryBoundedReconciliationPageBeforeCompleting() {
        TestClock clock = new TestClock(100);
        LaneHealthWord health = new LaneHealthWord();
        OrderFactLane facts = new OrderFactLane(4096, health, clock, 1);
        BybitReconciliationPage page = new BybitReconciliationPage(2, 16);
        MutableLocalOrderId first = id(1, 1);
        MutableLocalOrderId second = id(2, 1);
        int[] calls = {0};
        BybitReconciliationTransport transport =
                (endpoint, cursor, cursorLength, start, destination) -> {
                    int call = calls[0]++;
                    if (call == 0) {
                        assertEquals(BybitReconciliationEndpoint.OPEN_ORDERS, endpoint);
                        destination.add(first.high(), first.low(), 5, VenueOrderState.CANCELLED);
                        destination.nextCursor("next".getBytes(StandardCharsets.US_ASCII), 0, 4);
                    } else if (call == 1) {
                        assertEquals(BybitReconciliationEndpoint.OPEN_ORDERS, endpoint);
                        assertEquals(4, cursorLength);
                        destination.add(second.high(), second.low(), 10, VenueOrderState.FILLED);
                    } else {
                        assertEquals(BybitReconciliationEndpoint.ORDER_HISTORY, endpoint);
                        assertEquals(0, cursorLength);
                    }
                    return true;
                };
        BybitReconciliationCoordinator coordinator =
                new BybitReconciliationCoordinator(
                        BybitOrderProfile.inverseBtcUsd(7, 11),
                        transport,
                        page,
                        new OrderFactLaneSink(facts),
                        clock,
                        clock,
                        3);
        assertEquals(BybitReconciliationStatus.COMPLETE, coordinator.reconcileOrders(1, 0));
        List<Long> filled = new ArrayList<>();
        facts.drain(fact -> filled.add(fact.authoritativeFilled()), 10);
        assertEquals(List.of(5L, 10L), filled);
        assertEquals(3, calls[0]);
    }

    @Test
    void cancelWriteDoesNotRepeatSubmitTransitionAndRejectedCancelBecomesAmbiguous() {
        TestClock clock = new TestClock(1_000_000_000L);
        FakeConnection connection = new FakeConnection();
        BybitOrderProfile profile = BybitOrderProfile.inverseBtcUsd(7, 11);
        BybitOrderRequestEncoder encoder = new BybitOrderRequestEncoder(profile);
        LaneHealthWord health = new LaneHealthWord();
        BybitAuthenticatedSession session =
                new BybitAuthenticatedSession(
                        BybitAuthenticatedSession.Role.TRADE,
                        connection,
                        credentials(),
                        encoder,
                        health,
                        clock,
                        clock,
                        new ReconnectPolicy(5, 100, 4, 0),
                        1,
                        20,
                        50);
        session.start();
        session.onTransportReady();
        session.onAuthenticationResult(true);
        OrderFactLane facts = new OrderFactLane(4096, health, clock, 1);
        BybitOrderAgent agent =
                new BybitOrderAgent(
                        profile,
                        session,
                        connection,
                        encoder,
                        new BybitRequestCorrelationTable(4),
                        activeOrder(command(3, session.sessionGeneration())),
                        new BybitRateLimitState(),
                        new OrderFactLaneSink(facts),
                        clock,
                        clock);
        MutableOrderCommand cancel = command(3, session.sessionGeneration());
        cancel.set(
                OrderCommandType.CANCEL,
                OrderUrgency.URGENT,
                cancel.localOrderIdHigh(),
                cancel.localOrderIdLow(),
                cancel.venueId(),
                cancel.instrumentId(),
                cancel.side(),
                cancel.quantity(),
                cancel.limitPriceTicks(),
                cancel.createdMonoNanos());
        agent.onCommand(cancel);
        assertEquals(0, facts.sizeBytes());

        MutableBybitTradeResponse rejected = new MutableBybitTradeResponse();
        rejected.set(
                BybitTradeResponseKind.COMMAND_REJECTED,
                110001,
                cancel.localOrderIdHigh(),
                cancel.localOrderIdLow(),
                10,
                9,
                1000);
        assertEquals(BybitOrderParseStatus.OK, agent.onResponse(rejected));
        List<OrderFactType> types = new ArrayList<>();
        facts.drain(fact -> types.add(fact.type()), 10);
        assertEquals(List.of(OrderFactType.WRITE_AMBIGUOUS), types);
        session.close();
    }

    private static MutableOrderCommand command(long sequence, long generation) {
        MutableLocalOrderId id = id(sequence, generation);
        return new MutableOrderCommand()
                .set(
                        OrderCommandType.SUBMIT,
                        OrderUrgency.NORMAL,
                        id.high(),
                        id.low(),
                        7,
                        11,
                        OrderSide.BUY,
                        10,
                        8_526_850,
                        100);
    }

    private static BybitActiveOrderTable activeOrder(final MutableOrderCommand command) {
        BybitActiveOrderTable activeOrders = new BybitActiveOrderTable(4);
        activeOrders.register(
                command.localOrderIdHigh(), command.localOrderIdLow(), command.quantity());
        return activeOrders;
    }

    private static MutableLocalOrderId id(long sequence, long generation) {
        MutableLocalOrderId id = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(1, 7, generation, 2, sequence, id);
        return id;
    }

    private static BybitCredentials credentials() {
        return new BybitCredentials(
                "api".getBytes(StandardCharsets.US_ASCII),
                "secret".getBytes(StandardCharsets.US_ASCII));
    }

    private static final class TestClock
            implements com.penguinsecure.basis.core.time.EpochClock,
                    com.penguinsecure.basis.core.time.MonotonicClock {
        private long value;

        private TestClock(long value) {
            this.value = value;
        }

        @Override
        public long epochNanos() {
            return value++;
        }

        @Override
        public long nanoTime() {
            return value++;
        }
    }

    private static final class FakeConnection implements VenueConnectionControl {
        private int connects;
        private final List<String> sent = new ArrayList<>();

        @Override
        public void connect() {
            connects++;
        }

        @Override
        public void sendText(CharSequence payload) {
            sent.add(payload.toString());
        }

        @Override
        public void close() {}
    }
}
