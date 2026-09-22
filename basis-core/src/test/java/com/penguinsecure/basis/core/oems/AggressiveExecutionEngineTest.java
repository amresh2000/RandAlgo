package com.penguinsecure.basis.core.oems;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.OrderUrgency;
import com.penguinsecure.basis.core.command.PriorityOrderCommandLane;
import com.penguinsecure.basis.core.risk.KillHierarchy;
import com.penguinsecure.basis.core.risk.PartitionedTokenBucket;
import com.penguinsecure.basis.core.risk.PreTradeRiskEngine;
import com.penguinsecure.basis.core.risk.RiskFixtures;
import com.penguinsecure.basis.core.risk.RiskReservationState;
import com.penguinsecure.basis.core.risk.RiskReservationTable;
import com.penguinsecure.basis.core.risk.StrategyRiskLedger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class AggressiveExecutionEngineTest {
    @Test
    void partialInitiationFillPublishesProportionalUrgentHedge() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        RiskReservationTable reservations = new RiskReservationTable(4, ledger);
        PriorityOrderCommandLane commands = new PriorityOrderCommandLane(4, 4);
        ChildOrderTable children = new ChildOrderTable(8, 16);
        ExecutionGroupTable groups = new ExecutionGroupTable(4);
        AggressiveExecutionEngine engine =
                new AggressiveExecutionEngine(
                        new PreTradeRiskEngine(new KillHierarchy(16), ledger, reservations),
                        reservations,
                        groups,
                        children,
                        commands);
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(2, 4, 2, 0, 0, 0, 100, 1);
        AggressiveExecutionPlan plan =
                new AggressiveExecutionPlan(
                        1,
                        0,
                        7,
                        1,
                        1,
                        2,
                        1,
                        1,
                        5,
                        6,
                        OrderSide.BUY,
                        OrderSide.SELL,
                        100,
                        200,
                        100,
                        101,
                        100,
                        1_100);
        MutableExecutionStart start = new MutableExecutionStart();

        engine.start(
                RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath())
                        .nativeOrders(
                                100, 200, 100, 101, 100, 101, 1, 1, 10, 10, 10, 10, 1_000, 1_000),
                RiskFixtures.envelope(),
                plan,
                1_000,
                start);
        assertEquals(OemsStatus.OK, start.status());
        commands.drainPrioritized(command -> {}, 1);
        assertEquals(
                OemsStatus.OK,
                engine.onInitiationWritten(
                        start.group().slot(),
                        start.group().generation(),
                        start.initiation().slot(),
                        start.initiation().generation()));
        MutableSlotHandle hedge = new MutableSlotHandle();
        assertEquals(
                OemsStatus.OK,
                engine.onInitiationFill(
                        plan,
                        start.group().slot(),
                        start.group().generation(),
                        start.initiation().slot(),
                        start.initiation().generation(),
                        101,
                        25,
                        100,
                        1_001,
                        hedge));

        long[] hedgeQuantity = new long[1];
        commands.drainPrioritized(command -> hedgeQuantity[0] = command.quantity(), 1);
        assertEquals(50, hedgeQuantity[0]);
        assertEquals(50, groups.hedgeRequested(start.group().slot(), start.group().generation()));
        assertEquals(0, commands.size(OrderUrgency.NORMAL) + commands.size(OrderUrgency.URGENT));
    }

    @Test
    void ambiguousWriteRetainsReservationAndMarksUnknown() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        RiskReservationTable reservations = new RiskReservationTable(2, ledger);
        PriorityOrderCommandLane commands = new PriorityOrderCommandLane(2, 2);
        AggressiveExecutionEngine engine =
                new AggressiveExecutionEngine(
                        new PreTradeRiskEngine(new KillHierarchy(16), ledger, reservations),
                        reservations,
                        new ExecutionGroupTable(2),
                        new ChildOrderTable(4, 4),
                        commands);
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(1, 2, 1, 0, 0, 0, 100, 1);
        AggressiveExecutionPlan plan =
                new AggressiveExecutionPlan(
                        1,
                        0,
                        7,
                        1,
                        1,
                        2,
                        1,
                        1,
                        5,
                        6,
                        OrderSide.BUY,
                        OrderSide.SELL,
                        100,
                        100,
                        100,
                        101,
                        100,
                        1_100);
        MutableExecutionStart start = new MutableExecutionStart();
        engine.start(
                RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath()),
                RiskFixtures.envelope(),
                plan,
                1_000,
                start);

        engine.onWriteAmbiguous(
                start.group().slot(),
                start.group().generation(),
                start.initiation().slot(),
                start.initiation().generation());

        assertEquals(RiskReservationState.UNKNOWN, reservations.state(0, 1));
        assertEquals(100, ledger.pendingGross(0));
        assertEquals(1, ledger.unknownGroups(0));
    }

    @Test
    void exhaustedHedgeClaimsUnwindTheFilledInitiationQuantity() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        RiskReservationTable reservations = new RiskReservationTable(2, ledger);
        PriorityOrderCommandLane commands = new PriorityOrderCommandLane(4, 4);
        AggressiveExecutionEngine engine =
                new AggressiveExecutionEngine(
                        new PreTradeRiskEngine(new KillHierarchy(16), ledger, reservations),
                        reservations,
                        new ExecutionGroupTable(2),
                        new ChildOrderTable(8, 16),
                        commands);
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(1, 2, 1, 0, 0, 0, 100, 1);
        AggressiveExecutionPlan plan =
                new AggressiveExecutionPlan(
                        1,
                        0,
                        7,
                        1,
                        1,
                        2,
                        1,
                        1,
                        5,
                        6,
                        OrderSide.BUY,
                        OrderSide.SELL,
                        100,
                        200,
                        100,
                        101,
                        100,
                        1_100);
        MutableExecutionStart start = new MutableExecutionStart();
        engine.start(
                RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath())
                        .nativeOrders(
                                100, 200, 100, 101, 100, 101, 1, 1, 10, 10, 10, 10, 1_000, 1_000),
                RiskFixtures.envelope(),
                plan,
                1_000,
                start);
        commands.drainPrioritized(command -> {}, 1);
        engine.onInitiationWritten(
                start.group().slot(),
                start.group().generation(),
                start.initiation().slot(),
                start.initiation().generation());

        MutableSlotHandle child = new MutableSlotHandle();
        engine.onInitiationFill(
                plan,
                start.group().slot(),
                start.group().generation(),
                start.initiation().slot(),
                start.initiation().generation(),
                201,
                25,
                100,
                1_001,
                child);
        commands.drainPrioritized(command -> {}, 1);
        child = new MutableSlotHandle();
        engine.onInitiationFill(
                plan,
                start.group().slot(),
                start.group().generation(),
                start.initiation().slot(),
                start.initiation().generation(),
                202,
                25,
                100,
                1_002,
                child);
        commands.drainPrioritized(command -> {}, 1);
        child = new MutableSlotHandle();
        assertEquals(
                OemsStatus.OK,
                engine.onInitiationFill(
                        plan,
                        start.group().slot(),
                        start.group().generation(),
                        start.initiation().slot(),
                        start.initiation().generation(),
                        203,
                        25,
                        100,
                        1_003,
                        child));

        long[] unwindQuantity = new long[1];
        commands.drainPrioritized(command -> unwindQuantity[0] = command.quantity(), 1);
        assertEquals(25, unwindQuantity[0]);
    }
}
