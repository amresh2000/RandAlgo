package com.penguinsecure.basis.sim.runner;

import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.command.OrderUrgency;
import com.penguinsecure.basis.core.command.PriorityOrderCommandLane;
import com.penguinsecure.basis.core.oems.AggressiveExecutionPlan;
import com.penguinsecure.basis.core.oems.ChildOrderTable;
import com.penguinsecure.basis.core.oems.ExecutionGroupTable;
import com.penguinsecure.basis.core.oems.OemsFactProcessor;
import com.penguinsecure.basis.core.oems.OemsStatus;
import com.penguinsecure.basis.core.risk.RiskReservationTable;
import com.penguinsecure.basis.core.risk.StrategyRiskLedger;
import com.penguinsecure.basis.sim.digest.CanonicalSimulationDigest;
import com.penguinsecure.basis.sim.report.SimulationReport;
import com.penguinsecure.basis.sim.scenario.ScenarioStepType;
import com.penguinsecure.basis.sim.scenario.SimulationScenario;
import com.penguinsecure.basis.sim.scheduler.DeterministicFaultStream;
import com.penguinsecure.basis.sim.scheduler.DeterministicScheduler;
import com.penguinsecure.basis.sim.time.VirtualClock;
import com.penguinsecure.basis.sim.venue.FakeVenue;
import com.penguinsecure.basis.sim.venue.FakeVenueRouter;
import com.penguinsecure.basis.venue.api.lane.OrderFactLane;

/** Deterministic pump joining production command/fact seams to fake venues. */
public final class SimulationRunner {
    private final VirtualClock clock;
    private final DeterministicScheduler scheduler;
    private final DeterministicFaultStream faults;
    private final PriorityOrderCommandLane commands;
    private final OrderFactLane[] factLanes;
    private final FakeVenue[] venues;
    private final FakeVenueRouter router;
    private final OemsFactProcessor processor;
    private final AggressiveExecutionPlan activePlan;
    private final FixedDepthOrderBook[] books;
    private final ChildOrderTable children;
    private final ExecutionGroupTable groups;
    private final StrategyRiskLedger ledger;
    private final RiskReservationTable reservations;
    private final SimulationControlHandler controls;
    private final CanonicalSimulationDigest digest;
    private boolean orderAgentStalled;
    private boolean urgentQueueStalled;
    private boolean factIngressStalled;
    private long commandCount;
    private long factCount;
    private long simulatedFactCount;
    private long counterfactualFactCount;
    private long actualFactCount;
    private int checkpoints;
    private OemsStatus firstOemsFailure;
    private String failureReason = "";
    private boolean hasRun;

    @SuppressWarnings("ParameterNumber")
    public SimulationRunner(
            final String buildIdentity,
            final String configurationIdentity,
            final String inputIdentity,
            final long seed,
            final VirtualClock clock,
            final DeterministicScheduler scheduler,
            final DeterministicFaultStream faults,
            final PriorityOrderCommandLane commands,
            final OrderFactLane[] factLanes,
            final FakeVenue[] venues,
            final OemsFactProcessor processor,
            final AggressiveExecutionPlan activePlan,
            final FixedDepthOrderBook[] books,
            final ChildOrderTable children,
            final ExecutionGroupTable groups,
            final StrategyRiskLedger ledger,
            final RiskReservationTable reservations,
            final SimulationControlHandler controls) {
        if (clock == null
                || scheduler == null
                || faults == null
                || commands == null
                || factLanes == null
                || factLanes.length == 0
                || venues == null
                || venues.length == 0
                || processor == null
                || books == null
                || children == null
                || groups == null
                || ledger == null
                || reservations == null) {
            throw new IllegalArgumentException("simulation dependencies are required");
        }
        this.clock = clock;
        this.scheduler = scheduler;
        this.faults = faults;
        this.commands = commands;
        this.factLanes = factLanes.clone();
        this.venues = venues.clone();
        router = new FakeVenueRouter(this.venues);
        this.processor = processor;
        this.activePlan = activePlan;
        this.books = books.clone();
        this.children = children;
        this.groups = groups;
        this.ledger = ledger;
        this.reservations = reservations;
        this.controls = controls == null ? new SimulationControlHandler() {} : controls;
        digest =
                new CanonicalSimulationDigest(
                        buildIdentity, configurationIdentity, inputIdentity, seed);
    }

    public SimulationReport run(final SimulationScenario scenario, final int globalRoundLimit) {
        if (hasRun) throw new IllegalStateException("runner instances execute exactly once");
        if (scenario == null || globalRoundLimit <= 0) {
            throw new IllegalArgumentException("scenario and bounds are required");
        }
        hasRun = true;
        boolean complete = true;
        int remainingRounds = globalRoundLimit;
        for (int index = 0; index < scenario.size() && complete; index++) {
            final ScenarioStepType type = scenario.type(index);
            final long value = scenario.value(index);
            switch (type) {
                case ADVANCE_TO -> complete = advanceTo(value, remainingRounds);
                case PUMP -> {
                    final int rounds = Math.toIntExact(value);
                    if (rounds <= 0 || rounds > remainingRounds) {
                        complete = fail("invalid or exhausted pump round bound");
                    } else {
                        complete = pump(rounds, true);
                        remainingRounds -= rounds;
                    }
                }
                case STALL_ORDER_AGENT -> orderAgentStalled = true;
                case RESUME_ORDER_AGENT -> orderAgentStalled = false;
                case STALL_URGENT_QUEUE -> urgentQueueStalled = true;
                case RESUME_URGENT_QUEUE -> urgentQueueStalled = false;
                case STALL_FACT_INGRESS -> factIngressStalled = true;
                case RESUME_FACT_INGRESS -> factIngressStalled = false;
                case CHECKPOINT -> checkpoints++;
                case KILL -> controls.onKill(Math.toIntExact(value));
                case ARCHIVE_FAILURE -> controls.onArchiveFailure(Math.toIntExact(value));
                case RECOVER_HEDGE_PATH -> controls.onRecoverHedgePath();
            }
        }
        if (complete && !orderAgentStalled && !factIngressStalled) {
            complete = pump(Math.max(1, remainingRounds), true);
        }
        final String finalDigest =
                digest.finish(
                        books,
                        children,
                        groups,
                        ledger,
                        reservations,
                        venues,
                        scheduler,
                        faults,
                        checkpoints,
                        complete,
                        firstOemsFailure == null ? "NONE" : firstOemsFailure.name(),
                        failureReason);
        return new SimulationReport(
                scenario.name(),
                finalDigest,
                complete,
                clock.nanoTime(),
                commandCount,
                factCount,
                simulatedFactCount,
                counterfactualFactCount,
                actualFactCount,
                scheduler.executedEvents(),
                checkpoints,
                firstOemsFailure,
                failureReason);
    }

    private boolean advanceTo(final long targetMonoNanos, final int maximumRounds) {
        if (targetMonoNanos < clock.nanoTime()) return fail("scenario moved time backward");
        int rounds = 0;
        while (rounds++ < maximumRounds) {
            boolean progressed = drainImmediate();
            if (scheduler.size() > 0 && scheduler.nextScheduledMonoNanos() <= targetMonoNanos) {
                scheduler.runNext(this::onScheduledEvent);
                progressed = true;
            }
            progressed |= drainFacts();
            if (!progressed) {
                clock.advanceTo(targetMonoNanos);
                return true;
            }
        }
        return fail("advance round limit reached");
    }

    private boolean pump(final int maximumRounds, final boolean includeFutureEvents) {
        for (int round = 0; round < maximumRounds; round++) {
            boolean progressed = drainImmediate();
            if (scheduler.size() > 0
                    && (includeFutureEvents
                            || scheduler.nextScheduledMonoNanos() <= clock.nanoTime())) {
                scheduler.runNext(this::onScheduledEvent);
                progressed = true;
            }
            progressed |= drainFacts();
            if (!progressed) return true;
        }
        return fail("simulation did not quiesce within the round bound");
    }

    private boolean drainImmediate() {
        if (orderAgentStalled) return false;
        final int drained =
                urgentQueueStalled
                        ? commands.drainNormal(this::recordAndRoute, 64)
                        : commands.drainPrioritized(this::recordAndRoute, 64);
        return drained > 0;
    }

    private void recordAndRoute(
            final com.penguinsecure.basis.core.command.MutableOrderCommand command) {
        digest.command(command);
        commandCount++;
        router.onCommand(command);
    }

    private boolean drainFacts() {
        if (factIngressStalled) return false;
        int drained = 0;
        for (OrderFactLane lane : factLanes) {
            drained +=
                    lane.drain(
                            fact -> {
                                digest.fact(fact);
                                factCount++;
                                switch (fact.provenance()) {
                                    case SIMULATED -> simulatedFactCount++;
                                    case COUNTERFACTUAL -> counterfactualFactCount++;
                                    case ACTUAL -> actualFactCount++;
                                }
                                final OemsStatus status = processor.process(fact, activePlan);
                                if (status != OemsStatus.OK
                                        && status != OemsStatus.DUPLICATE
                                        && firstOemsFailure == null) {
                                    firstOemsFailure = status;
                                    failureReason =
                                            "order fact " + fact.type() + " returned " + status;
                                }
                            },
                            64);
        }
        return drained > 0;
    }

    @SuppressWarnings("ParameterNumber")
    private void onScheduledEvent(
            final long time,
            final int priority,
            final int producerId,
            final long producerSequence,
            final int eventKind,
            final long value0,
            final long value1,
            final long value2,
            final long value3) {
        final FakeVenue venue = router.byProducerId(producerId);
        if (venue == null) {
            fail("scheduled event has no producer");
            return;
        }
        venue.onScheduledEvent(eventKind, value0, value1);
    }

    private boolean fail(final String reason) {
        if (failureReason.isEmpty()) failureReason = reason;
        return false;
    }

    public boolean hasQueuedCommands() {
        return commands.size(OrderUrgency.NORMAL) > 0 || commands.size(OrderUrgency.URGENT) > 0;
    }
}
