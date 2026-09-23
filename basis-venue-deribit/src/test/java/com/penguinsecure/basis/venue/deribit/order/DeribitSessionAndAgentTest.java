package com.penguinsecure.basis.venue.deribit.order;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.OrderUrgency;
import com.penguinsecure.basis.core.identity.LocalOrderIdCodec;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactType;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeribitSessionAndAgentTest {
    @Test
    void authenticatesConfiguresHeartbeatAndCorrelatesOutOfOrderIds() {
        TestClock clock = new TestClock(1_700_000_000_000_000_000L);
        FakeConnection connection = new FakeConnection();
        DeribitOrderProfile profile = DeribitOrderProfile.inverseBtcPerpetual(7, 11);
        DeribitOrderRequestEncoder encoder = new DeribitOrderRequestEncoder(profile);
        DeribitRequestIdSequence ids = new DeribitRequestIdSequence(0);
        DeribitAuthenticatedSession session =
                new DeribitAuthenticatedSession(
                        DeribitAuthenticatedSession.Role.ORDER,
                        connection,
                        credentials(),
                        new DeribitTokenState(),
                        encoder,
                        ids,
                        nonce -> nonce.append("fixed-nonce"),
                        new LaneHealthWord(),
                        clock,
                        clock,
                        new ReconnectPolicy(5, 100, 4, 0),
                        1,
                        10,
                        20_000_000_000L);
        session.start();
        session.onTransportReady();
        assertTrue(connection.sent.getFirst().contains("public/auth"));

        DeribitJsonRpcResponseParser parser = new DeribitJsonRpcResponseParser();
        MutableDeribitResponse response =
                parse(
                        parser,
                        "{\"id\":1,\"result\":{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":900}}");
        assertTrue(session.onResponse(response));
        assertTrue(connection.sent.getLast().contains("public/set_heartbeat"));
        assertTrue(session.onResponse(parse(parser, "{\"id\":2,\"result\":\"ok\"}")));
        assertEquals(VenueSessionState.LIVE, session.state());

        DeribitRequestCorrelationTable correlations = new DeribitRequestCorrelationTable(4);
        DeribitActiveOrderTable active = new DeribitActiveOrderTable(4);
        List<VenueOrderFactType> facts = new ArrayList<>();
        DeribitOrderAgent agent =
                new DeribitOrderAgent(
                        profile,
                        session,
                        connection,
                        encoder,
                        ids,
                        correlations,
                        active,
                        new DeribitRateLimitState(),
                        fact -> {
                            facts.add(fact.type());
                            return true;
                        },
                        clock,
                        clock);
        MutableLocalOrderId local = new MutableLocalOrderId();
        LocalOrderIdCodec.encode(1, 7, session.sessionGeneration(), 2, 3, local);
        MutableOrderCommand command =
                new MutableOrderCommand()
                        .set(
                                OrderCommandType.SUBMIT,
                                OrderUrgency.NORMAL,
                                local.high(),
                                local.low(),
                                7,
                                11,
                                OrderSide.BUY,
                                10,
                                8_526_850,
                                1);
        agent.onCommand(command);
        assertEquals(1, correlations.size());
        assertEquals(
                DeribitOrderParseStatus.OK,
                agent.onResponse(parse(parser, "{\"id\":3,\"result\":{\"order\":{}}}")));
        assertEquals(0, correlations.size());
        assertEquals(List.of(VenueOrderFactType.WRITE_ACCEPTED), facts);
        session.close();
    }

    @Test
    void rateErrorAndDisconnectNeverBlindlyResubmit() {
        DeribitRequestCorrelationTable table = new DeribitRequestCorrelationTable(2);
        assertTrue(table.register(9, 1, 2, 3, OrderCommandType.SUBMIT));
        assertTrue(table.register(8, 3, 4, 3, OrderCommandType.CANCEL));
        assertEquals(0, table.find(9));
        assertEquals(1, table.find(8));
        table.remove(0);
        assertEquals(1, table.size());
    }

    private static MutableDeribitResponse parse(
            final DeribitJsonRpcResponseParser parser, final String json) {
        MutableDeribitResponse response = new MutableDeribitResponse();
        assertEquals(
                DeribitOrderParseStatus.OK,
                parser.parse(
                        Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.US_ASCII)),
                        response));
        return response;
    }

    private static DeribitCredentials credentials() {
        return new DeribitCredentials(
                "client".getBytes(StandardCharsets.US_ASCII),
                "secret".getBytes(StandardCharsets.US_ASCII));
    }

    private static final class TestClock
            implements com.penguinsecure.basis.core.time.EpochClock,
                    com.penguinsecure.basis.core.time.MonotonicClock {
        private long value;

        TestClock(final long value) {
            this.value = value;
        }

        public long epochNanos() {
            return value++;
        }

        public long nanoTime() {
            return value++;
        }
    }

    private static final class FakeConnection implements VenueConnectionControl {
        private final List<String> sent = new ArrayList<>();

        public void connect() {}

        public void sendText(final CharSequence payload) {
            sent.add(payload.toString());
        }

        public void close() {}
    }
}
