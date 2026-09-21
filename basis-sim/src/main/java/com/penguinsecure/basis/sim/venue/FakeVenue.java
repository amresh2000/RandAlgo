package com.penguinsecure.basis.sim.venue;

import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.fact.MutableOrderFact;
import com.penguinsecure.basis.core.oems.fact.OrderFactProvenance;
import com.penguinsecure.basis.core.oems.fact.OrderFactType;
import com.penguinsecure.basis.sim.paper.ConservativePaperFillModel;
import com.penguinsecure.basis.sim.paper.MutablePaperFill;
import com.penguinsecure.basis.sim.scheduler.DeterministicFaultStream;
import com.penguinsecure.basis.sim.scheduler.DeterministicScheduler;
import com.penguinsecure.basis.sim.scheduler.ScheduleStatus;
import com.penguinsecure.basis.sim.time.VirtualClock;
import com.penguinsecure.basis.venue.api.lane.OrderFactLane;

/** Bounded authoritative fake venue driven only by virtual time. */
public final class FakeVenue {
    private final FakeVenueProfile profile;
    private final int producerId;
    private final DeterministicScheduler scheduler;
    private final VirtualClock clock;
    private final DeterministicFaultStream faults;
    private final FakeVenueFaultPlan faultPlan;
    private final OrderFactLane facts;
    private final SimulationBookLookup books;
    private final ConservativePaperFillModel fillModel;
    private final OrderFactProvenance provenance;
    private final long[] idHigh;
    private final long[] idLow;
    private final int[] instrumentIds;
    private final OrderSide[] sides;
    private final long[] quantities;
    private final long[] limitPrices;
    private final long[] filled;
    private final long[] executionHashes;
    private final long[] lastFillQuantities;
    private final long[] lastFillPrices;
    private final FakeVenueOrderState[] states;
    private final MutableOrderFact fact = new MutableOrderFact();
    private final MutablePaperFill paperFill = new MutablePaperFill();
    private long producerSequence;
    private long executionSequence;
    private long commandsReceived;
    private long factsPublished;
    private long publicationFailures;
    private long scheduleFailures;

    @SuppressWarnings("ParameterNumber")
    public FakeVenue(
            final FakeVenueProfile profile,
            final int producerId,
            final int orderCapacity,
            final DeterministicScheduler scheduler,
            final VirtualClock clock,
            final DeterministicFaultStream faults,
            final FakeVenueFaultPlan faultPlan,
            final OrderFactLane facts,
            final SimulationBookLookup books,
            final OrderFactProvenance provenance) {
        if (profile == null
                || producerId < 0
                || orderCapacity <= 0
                || scheduler == null
                || clock == null
                || faults == null
                || faultPlan == null
                || facts == null
                || books == null
                || provenance == null
                || provenance == OrderFactProvenance.ACTUAL) {
            throw new IllegalArgumentException("invalid fake venue configuration");
        }
        this.profile = profile;
        this.producerId = producerId;
        this.scheduler = scheduler;
        this.clock = clock;
        this.faults = faults;
        this.faultPlan = faultPlan;
        this.facts = facts;
        this.books = books;
        this.provenance = provenance;
        fillModel = new ConservativePaperFillModel(profile.adverseSlippageTicks());
        idHigh = new long[orderCapacity];
        idLow = new long[orderCapacity];
        instrumentIds = new int[orderCapacity];
        sides = new OrderSide[orderCapacity];
        quantities = new long[orderCapacity];
        limitPrices = new long[orderCapacity];
        filled = new long[orderCapacity];
        executionHashes = new long[orderCapacity];
        lastFillQuantities = new long[orderCapacity];
        lastFillPrices = new long[orderCapacity];
        states = new FakeVenueOrderState[orderCapacity];
        java.util.Arrays.fill(states, FakeVenueOrderState.FREE);
    }

    public void onCommand(final MutableOrderCommand command) {
        if (command == null || command.venueId() != profile.venueId()) return;
        commandsReceived++;
        final int existing = find(command.localOrderIdHigh(), command.localOrderIdLow());
        if (command.type() == OrderCommandType.SUBMIT) {
            if (existing >= 0) {
                schedule(FakeVenueEventKind.RECONCILED, existing, 0, normalTime());
                return;
            }
            final int slot = allocate();
            if (slot < 0) {
                scheduleFailures++;
                return;
            }
            capture(slot, command);
            scriptSubmit(slot, faultPlan.next());
            return;
        }
        if (existing < 0) return;
        if (command.type() == OrderCommandType.CANCEL) {
            schedule(FakeVenueEventKind.WRITE_ACCEPTED, existing, 0, writeTime());
            schedule(FakeVenueEventKind.CANCELLED, existing, 0, acknowledgementTime());
        } else if (command.type() == OrderCommandType.QUERY) {
            schedule(FakeVenueEventKind.RECONCILED, existing, 0, acknowledgementTime());
        }
    }

    public void onScheduledEvent(
            final int eventKind, final long orderSlot, final long maximumFillQuantity) {
        if (orderSlot < 0 || orderSlot >= states.length) return;
        final int slot = (int) orderSlot;
        final FakeVenueEventKind kind = FakeVenueEventKind.fromCode(eventKind);
        switch (kind) {
            case WRITE_ACCEPTED -> publish(slot, OrderFactType.WRITE_ACCEPTED, 0, 0, 0, null);
            case WRITE_FAILED -> {
                states[slot] = FakeVenueOrderState.REJECTED;
                publish(slot, OrderFactType.WRITE_FAILED, 0, 0, 0, null);
            }
            case WRITE_AMBIGUOUS -> {
                publish(slot, OrderFactType.WRITE_AMBIGUOUS, 0, 0, 0, null);
            }
            case ACKNOWLEDGED -> {
                states[slot] = FakeVenueOrderState.WORKING;
                publish(slot, OrderFactType.ACKNOWLEDGED, 0, 0, 0, null);
            }
            case FILL -> publishFill(slot, maximumFillQuantity);
            case REJECTED -> {
                states[slot] = FakeVenueOrderState.REJECTED;
                publish(slot, OrderFactType.REJECTED, 0, 0, 0, null);
            }
            case DISCONNECTED -> {
                publish(slot, OrderFactType.DISCONNECTED, 0, 0, 0, null);
            }
            case RATE_LIMITED -> {
                states[slot] = FakeVenueOrderState.REJECTED;
                publish(slot, OrderFactType.RATE_LIMITED, 0, 0, 0, null);
            }
            case CANCELLED -> {
                states[slot] = FakeVenueOrderState.CANCELLED;
                publish(slot, OrderFactType.CANCELLED, 0, 0, 0, null);
            }
            case RECONCILED ->
                    publish(
                            slot,
                            OrderFactType.RECONCILED,
                            0,
                            0,
                            filled[slot],
                            authoritativeState(states[slot]));
        }
    }

    private void scriptSubmit(final int slot, final FakeVenueOutcome outcome) {
        switch (outcome) {
            case ACCEPT_FULL -> acceptAndFill(slot, quantities[slot], false);
            case ACCEPT_PARTIAL -> acceptAndFill(slot, Math.max(1, quantities[slot] / 2), false);
            case REJECT -> {
                schedule(FakeVenueEventKind.WRITE_ACCEPTED, slot, 0, writeTime());
                schedule(FakeVenueEventKind.REJECTED, slot, 0, acknowledgementTime());
            }
            case WRITE_FAILED -> schedule(FakeVenueEventKind.WRITE_FAILED, slot, 0, writeTime());
            case ACCEPT_WITH_LOST_RESPONSE -> {
                states[slot] = FakeVenueOrderState.WORKING;
                schedule(FakeVenueEventKind.WRITE_AMBIGUOUS, slot, 0, writeTime());
            }
            case DISCONNECT -> {
                states[slot] = FakeVenueOrderState.WORKING;
                schedule(FakeVenueEventKind.DISCONNECTED, slot, 0, writeTime());
            }
            case RATE_LIMIT -> schedule(FakeVenueEventKind.RATE_LIMITED, slot, 0, writeTime());
            case DUPLICATE_FILL -> acceptAndFill(slot, quantities[slot], true);
            case REORDER_FILL_BEFORE_ACK -> {
                schedule(FakeVenueEventKind.WRITE_ACCEPTED, slot, 0, writeTime());
                schedule(FakeVenueEventKind.FILL, slot, quantities[slot], fillTime());
                schedule(
                        FakeVenueEventKind.ACKNOWLEDGED,
                        slot,
                        0,
                        Math.max(fillTime(), acknowledgementTime()));
            }
        }
    }

    private void acceptAndFill(final int slot, final long maximumFill, final boolean duplicate) {
        schedule(FakeVenueEventKind.WRITE_ACCEPTED, slot, 0, writeTime());
        schedule(FakeVenueEventKind.ACKNOWLEDGED, slot, 0, acknowledgementTime());
        final long fillAt = fillTime();
        schedule(FakeVenueEventKind.FILL, slot, maximumFill, fillAt);
        if (duplicate) schedule(FakeVenueEventKind.FILL, slot, maximumFill, fillAt + 1);
    }

    private void publishFill(final int slot, final long maximumFillQuantity) {
        if (executionHashes[slot] != 0 && states[slot] == FakeVenueOrderState.FILLED) {
            publish(
                    slot,
                    OrderFactType.FILL,
                    executionHashes[slot],
                    lastFillQuantities[slot],
                    lastFillPrices[slot],
                    null);
            return;
        }
        final FixedDepthOrderBook book = books.find(profile.venueId(), instrumentIds[slot]);
        fillModel.fill(
                book,
                sides[slot],
                quantities[slot] - filled[slot],
                maximumFillQuantity,
                limitPrices[slot],
                schedulerTime(),
                paperFill);
        if (!paperFill.proven()) return;
        if (executionHashes[slot] == 0)
            executionHashes[slot] = executionHash(slot, ++executionSequence);
        final long newFilled;
        try {
            newFilled = Math.addExact(filled[slot], paperFill.quantity());
        } catch (ArithmeticException ignored) {
            states[slot] = FakeVenueOrderState.UNKNOWN;
            return;
        }
        if (newFilled > quantities[slot]) {
            states[slot] = FakeVenueOrderState.UNKNOWN;
            return;
        }
        final boolean duplicate =
                filled[slot] == newFilled || states[slot] == FakeVenueOrderState.FILLED;
        if (!duplicate) filled[slot] = newFilled;
        lastFillQuantities[slot] = paperFill.quantity();
        lastFillPrices[slot] = paperFill.priceTicks();
        states[slot] =
                filled[slot] == quantities[slot]
                        ? FakeVenueOrderState.FILLED
                        : FakeVenueOrderState.PARTIALLY_FILLED;
        publish(
                slot,
                OrderFactType.FILL,
                executionHashes[slot],
                paperFill.quantity(),
                paperFill.priceTicks(),
                null);
    }

    private void publish(
            final int slot,
            final OrderFactType type,
            final long executionHash,
            final long fillQuantity,
            final long auxiliary,
            final ChildOrderState authoritativeState) {
        final long sessionGeneration = idHigh[slot] & 0xFFFF_FFFFL;
        fact.set(
                type,
                provenance,
                idHigh[slot],
                idLow[slot],
                profile.venueId(),
                instrumentIds[slot],
                sessionGeneration,
                schedulerEpoch(),
                schedulerTime(),
                executionHash,
                fillQuantity,
                type == OrderFactType.FILL ? auxiliary : 0,
                type == OrderFactType.RECONCILED ? auxiliary : 0,
                authoritativeState,
                0);
        if (facts.publish(fact)) factsPublished++;
        else publicationFailures++;
    }

    private void schedule(
            final FakeVenueEventKind kind,
            final int slot,
            final long maximumFill,
            final long scheduledTime) {
        final ScheduleStatus status =
                scheduler.schedule(
                        scheduledTime,
                        40,
                        producerId,
                        producerSequence++,
                        kind.code(),
                        slot,
                        maximumFill,
                        0,
                        0);
        if (status != ScheduleStatus.OK) scheduleFailures++;
    }

    private long writeTime() {
        return addLatency(profile.writeLatencyNanos());
    }

    private long acknowledgementTime() {
        return addLatency(profile.acknowledgementLatencyNanos());
    }

    private long fillTime() {
        return addLatency(profile.fillLatencyNanos());
    }

    private long normalTime() {
        return addLatency(profile.acknowledgementLatencyNanos());
    }

    private long addLatency(final long latency) {
        final long jitter =
                profile.maximumJitterNanos() == 0
                        ? 0
                        : Long.remainderUnsigned(
                                faults.nextLong(), profile.maximumJitterNanos() + 1);
        return Math.addExact(schedulerTime(), Math.addExact(latency, jitter));
    }

    private int find(final long high, final long low) {
        for (int slot = 0; slot < states.length; slot++) {
            if (states[slot] != FakeVenueOrderState.FREE
                    && idHigh[slot] == high
                    && idLow[slot] == low) return slot;
        }
        return -1;
    }

    private int allocate() {
        for (int slot = 0; slot < states.length; slot++) {
            if (states[slot] == FakeVenueOrderState.FREE) return slot;
        }
        return -1;
    }

    private void capture(final int slot, final MutableOrderCommand command) {
        idHigh[slot] = command.localOrderIdHigh();
        idLow[slot] = command.localOrderIdLow();
        instrumentIds[slot] = command.instrumentId();
        sides[slot] = command.side();
        quantities[slot] = command.quantity();
        limitPrices[slot] = command.limitPriceTicks();
        filled[slot] = 0;
        executionHashes[slot] = 0;
        lastFillQuantities[slot] = 0;
        lastFillPrices[slot] = 0;
        states[slot] = FakeVenueOrderState.RECEIVED;
    }

    private long schedulerTime() {
        return clock.nanoTime();
    }

    private long schedulerEpoch() {
        return clock.epochNanos();
    }

    private long executionHash(final int slot, final long sequence) {
        long value =
                sequence
                        ^ ((long) profile.venueId() << 48)
                        ^ idHigh[slot]
                        ^ Long.rotateLeft(idLow[slot], 17);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return value == 0 ? 1 : value;
    }

    private static ChildOrderState authoritativeState(final FakeVenueOrderState state) {
        return switch (state) {
            case FILLED -> ChildOrderState.FILLED;
            case CANCELLED -> ChildOrderState.CANCELLED;
            case REJECTED -> ChildOrderState.REJECTED;
            case PARTIALLY_FILLED -> ChildOrderState.PARTIALLY_FILLED;
            case WORKING, RECEIVED -> ChildOrderState.WORKING;
            case UNKNOWN -> ChildOrderState.UNKNOWN;
            case FREE -> ChildOrderState.FREE;
        };
    }

    public int venueId() {
        return profile.venueId();
    }

    public int producerId() {
        return producerId;
    }

    public int capacity() {
        return states.length;
    }

    public FakeVenueOrderState stateAt(final int slot) {
        return states[slot];
    }

    public long idHighAt(final int slot) {
        return idHigh[slot];
    }

    public long idLowAt(final int slot) {
        return idLow[slot];
    }

    public long filledAt(final int slot) {
        return filled[slot];
    }

    public long commandsReceived() {
        return commandsReceived;
    }

    public long factsPublished() {
        return factsPublished;
    }

    public long publicationFailures() {
        return publicationFailures;
    }

    public long scheduleFailures() {
        return scheduleFailures;
    }
}
