package com.penguinsecure.basis.sim.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.core.book.BookSequenceField;
import com.penguinsecure.basis.core.book.BookSequenceMode;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.PriorityOrderCommandLane;
import com.penguinsecure.basis.core.oems.AggressiveExecutionEngine;
import com.penguinsecure.basis.core.oems.AggressiveExecutionPlan;
import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.ChildOrderTable;
import com.penguinsecure.basis.core.oems.ExecutionGroupState;
import com.penguinsecure.basis.core.oems.ExecutionGroupTable;
import com.penguinsecure.basis.core.oems.MutableExecutionStart;
import com.penguinsecure.basis.core.oems.OemsFactProcessor;
import com.penguinsecure.basis.core.oems.OemsStatus;
import com.penguinsecure.basis.core.oems.fact.OrderFactProvenance;
import com.penguinsecure.basis.core.risk.HedgePathHealth;
import com.penguinsecure.basis.core.risk.HedgePathHealthConfig;
import com.penguinsecure.basis.core.risk.HedgePathSample;
import com.penguinsecure.basis.core.risk.KillHierarchy;
import com.penguinsecure.basis.core.risk.PartitionedTokenBucket;
import com.penguinsecure.basis.core.risk.PreTradeRiskEngine;
import com.penguinsecure.basis.core.risk.PreTradeRiskRequest;
import com.penguinsecure.basis.core.risk.RiskEnvelope;
import com.penguinsecure.basis.core.risk.RiskReservationState;
import com.penguinsecure.basis.core.risk.RiskReservationTable;
import com.penguinsecure.basis.core.risk.StrategyRiskLedger;
import com.penguinsecure.basis.sim.report.SimulationReport;
import com.penguinsecure.basis.sim.runner.SimulationControlHandler;
import com.penguinsecure.basis.sim.runner.SimulationRunner;
import com.penguinsecure.basis.sim.scenario.ScenarioBuilder;
import com.penguinsecure.basis.sim.scenario.SimulationScenario;
import com.penguinsecure.basis.sim.scheduler.DeterministicFaultStream;
import com.penguinsecure.basis.sim.scheduler.DeterministicScheduler;
import com.penguinsecure.basis.sim.time.VirtualClock;
import com.penguinsecure.basis.sim.venue.FakeVenue;
import com.penguinsecure.basis.sim.venue.FakeVenueFaultPlan;
import com.penguinsecure.basis.sim.venue.FakeVenueOutcome;
import com.penguinsecure.basis.sim.venue.FakeVenueProfile;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.lane.OrderFactLane;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class DeterministicExecutionAcceptanceTest {
    @Test
    void identicalPairedRunsProduceIdenticalTerminalDigest() {
        RunResult first = execute(FakeVenueOutcome.ACCEPT_FULL, OrderFactProvenance.SIMULATED);
        RunResult second = execute(FakeVenueOutcome.ACCEPT_FULL, OrderFactProvenance.SIMULATED);

        assertTrue(first.report.complete());
        assertEquals(first.report.digest(), second.report.digest());
        assertEquals(first.report.commands(), second.report.commands());
        assertEquals(first.report.facts(), second.report.facts());
        assertNull(first.report.firstOemsFailure(), first.report.toString());
        assertEquals(
                ExecutionGroupState.COMPLETE,
                first.groups.state(first.groupSlot, first.groupGeneration));
        assertEquals(RiskReservationState.FREE, first.reservations.stateAt(0));
    }

    @Test
    void ambiguousWriteRetainsExposureAndDoesNotBlindlyRetransmit() {
        RunResult result =
                execute(FakeVenueOutcome.ACCEPT_WITH_LOST_RESPONSE, OrderFactProvenance.SIMULATED);

        assertTrue(result.report.complete());
        assertEquals(1, result.report.commands());
        assertEquals(
                ExecutionGroupState.UNKNOWN,
                result.groups.state(result.groupSlot, result.groupGeneration));
        assertEquals(RiskReservationState.UNKNOWN, result.reservations.stateAt(0));
        assertEquals(100, result.ledger.pendingGross(0));
    }

    @Test
    void counterfactualFactsNeverMutateActualOemsState() {
        RunResult result =
                execute(FakeVenueOutcome.ACCEPT_FULL, OrderFactProvenance.COUNTERFACTUAL);

        assertTrue(result.report.complete());
        assertEquals(
                ExecutionGroupState.INITIATING,
                result.groups.state(result.groupSlot, result.groupGeneration));
        assertEquals(
                ChildOrderState.SEND_PENDING,
                result.children.state(result.childSlot, result.childGeneration));
    }

    @Test
    void definitivePreFillFailuresReleaseInitiationReservation() {
        for (FakeVenueOutcome outcome :
                new FakeVenueOutcome[] {
                    FakeVenueOutcome.WRITE_FAILED,
                    FakeVenueOutcome.RATE_LIMIT,
                    FakeVenueOutcome.REJECT
                }) {
            RunResult result = execute(outcome, OrderFactProvenance.SIMULATED);
            assertTrue(result.report.complete(), outcome.name());
            assertEquals(
                    ExecutionGroupState.FAILED,
                    result.groups.state(result.groupSlot, result.groupGeneration),
                    outcome.name());
            assertEquals(RiskReservationState.FREE, result.reservations.stateAt(0), outcome.name());
        }
    }

    @Test
    void disconnectAndLostResponseRemainUnknown() {
        for (FakeVenueOutcome outcome :
                new FakeVenueOutcome[] {
                    FakeVenueOutcome.DISCONNECT, FakeVenueOutcome.ACCEPT_WITH_LOST_RESPONSE
                }) {
            RunResult result = execute(outcome, OrderFactProvenance.SIMULATED);
            assertEquals(
                    ExecutionGroupState.UNKNOWN,
                    result.groups.state(result.groupSlot, result.groupGeneration),
                    outcome.name());
            assertEquals(
                    RiskReservationState.UNKNOWN, result.reservations.stateAt(0), outcome.name());
        }
    }

    @Test
    void duplicateAndReorderedFactsAreIdempotent() {
        for (FakeVenueOutcome outcome :
                new FakeVenueOutcome[] {
                    FakeVenueOutcome.DUPLICATE_FILL, FakeVenueOutcome.REORDER_FILL_BEFORE_ACK
                }) {
            RunResult result = execute(outcome, OrderFactProvenance.SIMULATED);
            assertEquals(
                    ExecutionGroupState.COMPLETE,
                    result.groups.state(result.groupSlot, result.groupGeneration),
                    result.report.toString());
            assertNull(result.report.firstOemsFailure(), result.report.toString());
        }
    }

    @Test
    void partialInitiatingFillHedgesOnlyConfirmedIncrement() {
        RunResult result = execute(FakeVenueOutcome.ACCEPT_PARTIAL, OrderFactProvenance.SIMULATED);

        assertEquals(2, result.report.commands());
        assertEquals(
                ExecutionGroupState.HEDGING,
                result.groups.state(result.groupSlot, result.groupGeneration));
        assertEquals(50, result.groups.initiationFilled(result.groupSlot, result.groupGeneration));
        assertEquals(50, result.groups.hedgeRequested(result.groupSlot, result.groupGeneration));
        assertEquals(50, result.groups.hedgeFilled(result.groupSlot, result.groupGeneration));
    }

    @Test
    void orderAndFactStallsRecoverInDeclaredScenarioOrder() {
        SimulationScenario scenario =
                new ScenarioBuilder("stalls", 8)
                        .stallOrderAgent()
                        .pump(1)
                        .resumeOrderAgent()
                        .stallFactIngress()
                        .pump(16)
                        .resumeFactIngress()
                        .pump(128)
                        .checkpoint(7)
                        .build();
        RunResult result =
                execute(
                        FakeVenueOutcome.ACCEPT_FULL,
                        OrderFactProvenance.SIMULATED,
                        scenario,
                        null);

        assertTrue(result.report.complete(), result.report.toString());
        assertEquals(1, result.report.checkpoints());
        assertEquals(
                ExecutionGroupState.COMPLETE,
                result.groups.state(result.groupSlot, result.groupGeneration));
    }

    @Test
    void urgentHedgeQueueCanStallAndRecoverWithoutDroppingCommand() {
        SimulationScenario scenario =
                new ScenarioBuilder("urgent-stall", 4)
                        .stallUrgentQueue()
                        .pump(32)
                        .resumeUrgentQueue()
                        .pump(128)
                        .build();
        RunResult result =
                execute(
                        FakeVenueOutcome.ACCEPT_FULL,
                        OrderFactProvenance.SIMULATED,
                        scenario,
                        null);

        assertEquals(2, result.report.commands());
        assertEquals(
                ExecutionGroupState.COMPLETE,
                result.groups.state(result.groupSlot, result.groupGeneration));
    }

    @Test
    void killAndHedgePathRecoveryStepsReachControlBoundary() {
        int[] controls = new int[3];
        SimulationControlHandler handler =
                new SimulationControlHandler() {
                    @Override
                    public void onKill(final int scopeId) {
                        controls[0] = scopeId;
                    }

                    @Override
                    public void onRecoverHedgePath() {
                        controls[1]++;
                    }

                    @Override
                    public void onArchiveFailure(final int archiveId) {
                        controls[2] = archiveId;
                    }
                };
        SimulationScenario scenario =
                new ScenarioBuilder("controls", 4)
                        .kill(9)
                        .archiveFailure(17)
                        .recoverHedgePath()
                        .pump(128)
                        .build();

        execute(FakeVenueOutcome.ACCEPT_FULL, OrderFactProvenance.SIMULATED, scenario, handler);
        assertEquals(9, controls[0]);
        assertEquals(1, controls[1]);
        assertEquals(17, controls[2]);
    }

    private static RunResult execute(
            final FakeVenueOutcome firstOutcome, final OrderFactProvenance provenance) {
        return execute(firstOutcome, provenance, null, null);
    }

    private static RunResult execute(
            final FakeVenueOutcome firstOutcome,
            final OrderFactProvenance provenance,
            final SimulationScenario requestedScenario,
            final SimulationControlHandler controls) {
        VirtualClock clock = new VirtualClock(1_000_000_000, 1_000);
        DeterministicFaultStream faults = new DeterministicFaultStream(42);
        DeterministicScheduler scheduler = new DeterministicScheduler(64, 4, clock);
        FixedDepthOrderBook bybitBook = book(1, 5, 99, 100, clock.nanoTime());
        FixedDepthOrderBook deribitBook = book(2, 6, 101, 102, clock.nanoTime());

        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        RiskReservationTable reservations = new RiskReservationTable(4, ledger);
        ExecutionGroupTable groups = new ExecutionGroupTable(4);
        ChildOrderTable children = new ChildOrderTable(8, 32);
        PriorityOrderCommandLane commands = new PriorityOrderCommandLane(8, 8);
        AggressiveExecutionEngine engine =
                new AggressiveExecutionEngine(
                        new PreTradeRiskEngine(new KillHierarchy(16), ledger, reservations),
                        reservations,
                        groups,
                        children,
                        commands);
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
                        10_000_000);
        PartitionedTokenBucket bucket =
                new PartitionedTokenBucket(4, 4, 2, 0, 0, 0, 1_000_000, clock.nanoTime());
        MutableExecutionStart start = new MutableExecutionStart();
        engine.start(request(bucket), envelope(), plan, clock.nanoTime(), start);
        assertEquals(OemsStatus.OK, start.status());

        OrderFactLane bybitFacts = new OrderFactLane(4_096, new LaneHealthWord(), clock, 1);
        OrderFactLane deribitFacts = new OrderFactLane(4_096, new LaneHealthWord(), clock, 2);
        FakeVenue bybit =
                new FakeVenue(
                        FakeVenueProfile.bybit(1),
                        1,
                        8,
                        scheduler,
                        clock,
                        faults,
                        new FakeVenueFaultPlan(8, firstOutcome),
                        bybitFacts,
                        (venue, instrument) -> venue == 1 && instrument == 5 ? bybitBook : null,
                        provenance);
        FakeVenue deribit =
                new FakeVenue(
                        FakeVenueProfile.deribit(2),
                        2,
                        8,
                        scheduler,
                        clock,
                        faults,
                        new FakeVenueFaultPlan(8, FakeVenueOutcome.ACCEPT_FULL),
                        deribitFacts,
                        (venue, instrument) -> venue == 2 && instrument == 6 ? deribitBook : null,
                        provenance);
        SimulationRunner runner =
                new SimulationRunner(
                        "test-build",
                        "test-config",
                        "paired-input",
                        42,
                        clock,
                        scheduler,
                        faults,
                        commands,
                        new OrderFactLane[] {bybitFacts, deribitFacts},
                        new FakeVenue[] {bybit, deribit},
                        new OemsFactProcessor(engine, groups, children),
                        plan,
                        new FixedDepthOrderBook[] {bybitBook, deribitBook},
                        children,
                        groups,
                        ledger,
                        reservations,
                        controls);
        SimulationScenario scenario =
                requestedScenario == null
                        ? new ScenarioBuilder("paired-execution", 2).pump(128).checkpoint(1).build()
                        : requestedScenario;
        SimulationReport report = runner.run(scenario, 512);
        return new RunResult(
                report,
                groups,
                children,
                reservations,
                ledger,
                start.group().slot(),
                start.group().generation(),
                start.initiation().slot(),
                start.initiation().generation());
    }

    private static PreTradeRiskRequest request(final PartitionedTokenBucket bucket) {
        return new PreTradeRiskRequest()
                .identity(0, 7, 1, 1, 9, 10)
                .routes(1, 2, 3, 4, 5, 6, 1, 1, 1, 1, true)
                .opportunityEvidence(
                        1, 1, 1, 1, 1_000, 1_000, 10_000_000, 10_000_000, 1, 10_000_000)
                .currentEvidence(1, 1, 1, 1, 1_000, 1_000, true, true, 1_000)
                .nativeOrders(100, 100, 100, 101, 100, 101, 1, 1, 1, 1, 1, 1, 1_000, 1_000)
                .exposure(100, 0, 100, 50, 1_000, 100, 2, 100, 200, false, false)
                .safety(bucket, healthyPath());
    }

    private static RiskEnvelope envelope() {
        return new RiskEnvelope(
                7, 1, 1, 20_000_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 1_000, 10, 10);
    }

    private static HedgePathHealth healthyPath() {
        HedgePathHealth health =
                new HedgePathHealth(
                        new HedgePathHealthConfig(
                                50, 100, 500_000, 800_000, 100, 100, 100, 200, 2));
        HedgePathSample sample =
                new HedgePathSample(0, 0, 8, 0, 0, true, true, true, 8, 2, 0, 10, 20, true);
        health.observe(sample);
        health.observe(sample);
        return health;
    }

    private static FixedDepthOrderBook book(
            final int venueId,
            final int instrumentId,
            final long bid,
            final long ask,
            final long receiveMonoNanos) {
        FixedDepthOrderBook book =
                new FixedDepthOrderBook(
                        venueId,
                        instrumentId,
                        1,
                        4,
                        1,
                        1,
                        10_000_000,
                        0,
                        BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC,
                        BookSequenceField.VENUE_SEQUENCE);
        MutableMarketDataEvent image = new MutableMarketDataEvent(4);
        image.venueId(venueId);
        image.instrumentId(instrumentId);
        image.feedProfileId(1);
        image.kind(MarketDataEventKind.IMAGE);
        image.sessionGeneration(1);
        image.receiveEpochNanos(1_000_000_000);
        image.receiveMonoNanos(receiveMonoNanos);
        image.decodeCompleteMonoNanos(receiveMonoNanos + 1);
        image.venueSequence(1);
        image.addBid(bid, 100);
        image.addAsk(ask, 100);
        book.apply(image);
        return book;
    }

    private record RunResult(
            SimulationReport report,
            ExecutionGroupTable groups,
            ChildOrderTable children,
            RiskReservationTable reservations,
            StrategyRiskLedger ledger,
            int groupSlot,
            int groupGeneration,
            int childSlot,
            int childGeneration) {}
}
