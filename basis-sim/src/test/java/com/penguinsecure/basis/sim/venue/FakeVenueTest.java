package com.penguinsecure.basis.sim.venue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.OrderUrgency;
import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.fact.OrderFactProvenance;
import com.penguinsecure.basis.core.oems.fact.OrderFactType;
import com.penguinsecure.basis.sim.scheduler.DeterministicFaultStream;
import com.penguinsecure.basis.sim.scheduler.DeterministicScheduler;
import com.penguinsecure.basis.sim.scheduler.RunStatus;
import com.penguinsecure.basis.sim.time.VirtualClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.lane.OrderFactLane;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FakeVenueTest {
    private static final int VENUE_ID = 7;
    private static final long ORDER_ID_HIGH = 3;
    private static final long ORDER_ID_LOW = 11;

    @Test
    void queryReconcilesAmbiguousOrderAndCancelPublishesTerminalFact() {
        VirtualClock clock = new VirtualClock(1_000_000, 1_000);
        DeterministicScheduler scheduler = new DeterministicScheduler(32, 4, clock);
        OrderFactLane lane = new OrderFactLane(4_096, new LaneHealthWord(), clock, 1);
        FakeVenue venue =
                new FakeVenue(
                        FakeVenueProfile.bybit(VENUE_ID),
                        1,
                        4,
                        scheduler,
                        clock,
                        new DeterministicFaultStream(5),
                        new FakeVenueFaultPlan(2, FakeVenueOutcome.ACCEPT_WITH_LOST_RESPONSE),
                        lane,
                        (venueId, instrumentId) -> null,
                        OrderFactProvenance.SIMULATED);
        MutableOrderCommand command = new MutableOrderCommand();
        List<OrderFactType> types = new ArrayList<>();
        ChildOrderState[] reconciledState = new ChildOrderState[1];

        venue.onCommand(command(command, OrderCommandType.SUBMIT));
        runScheduled(scheduler, venue);
        drain(lane, types, reconciledState);
        assertEquals(List.of(OrderFactType.WRITE_AMBIGUOUS), types);

        types.clear();
        venue.onCommand(command(command, OrderCommandType.QUERY));
        runScheduled(scheduler, venue);
        drain(lane, types, reconciledState);
        assertEquals(List.of(OrderFactType.RECONCILED), types);
        assertEquals(ChildOrderState.WORKING, reconciledState[0]);

        types.clear();
        venue.onCommand(command(command, OrderCommandType.CANCEL));
        runScheduled(scheduler, venue);
        drain(lane, types, reconciledState);
        assertTrue(types.contains(OrderFactType.WRITE_ACCEPTED));
        assertTrue(types.contains(OrderFactType.CANCELLED));
        assertEquals(FakeVenueOrderState.CANCELLED, venue.stateAt(0));
    }

    private static MutableOrderCommand command(
            final MutableOrderCommand command, final OrderCommandType type) {
        return command.set(
                type,
                OrderUrgency.NORMAL,
                ORDER_ID_HIGH,
                ORDER_ID_LOW,
                VENUE_ID,
                9,
                OrderSide.BUY,
                10,
                100,
                1_000);
    }

    private static void runScheduled(
            final DeterministicScheduler scheduler, final FakeVenue venue) {
        RunStatus status =
                scheduler.runToQuiescence(
                        16,
                        Long.MAX_VALUE,
                        (time, priority, producer, sequence, kind, v0, v1, v2, v3) ->
                                venue.onScheduledEvent(kind, v0, v1));
        assertEquals(RunStatus.QUIESCENT, status);
    }

    private static void drain(
            final OrderFactLane lane,
            final List<OrderFactType> types,
            final ChildOrderState[] reconciledState) {
        lane.drain(
                fact -> {
                    types.add(fact.type());
                    if (fact.type() == OrderFactType.RECONCILED) {
                        reconciledState[0] = fact.authoritativeState();
                    }
                },
                16);
    }
}
