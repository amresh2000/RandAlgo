package com.penguinsecure.basis.sim.digest;

import com.penguinsecure.basis.core.book.BookSide;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.ChildOrderTable;
import com.penguinsecure.basis.core.oems.ExecutionGroupState;
import com.penguinsecure.basis.core.oems.ExecutionGroupTable;
import com.penguinsecure.basis.core.oems.fact.MutableOrderFact;
import com.penguinsecure.basis.core.risk.RiskReservationState;
import com.penguinsecure.basis.core.risk.RiskReservationTable;
import com.penguinsecure.basis.core.risk.StrategyRiskLedger;
import com.penguinsecure.basis.sim.scheduler.DeterministicFaultStream;
import com.penguinsecure.basis.sim.scheduler.DeterministicScheduler;
import com.penguinsecure.basis.sim.venue.FakeVenue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Versioned canonical SHA-256 encoding of input identity, trace, and terminal state. */
public final class CanonicalSimulationDigest {
    private static final int SCHEMA_VERSION = 1;
    private final MessageDigest digest;
    private final ByteBuffer scalar = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
    private String finished;

    public CanonicalSimulationDigest(
            final String buildIdentity,
            final String configurationIdentity,
            final String inputIdentity,
            final long seed) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        putInt(SCHEMA_VERSION);
        putString(buildIdentity);
        putString(configurationIdentity);
        putString(inputIdentity);
        putLong(seed);
    }

    public void command(final MutableOrderCommand command) {
        requireOpen();
        putByte(1);
        putString(command.type().name());
        putString(command.urgency().name());
        putLong(command.localOrderIdHigh());
        putLong(command.localOrderIdLow());
        putInt(command.venueId());
        putInt(command.instrumentId());
        putString(command.side().name());
        putLong(command.quantity());
        putLong(command.limitPriceTicks());
        putLong(command.createdMonoNanos());
    }

    public void fact(final MutableOrderFact fact) {
        requireOpen();
        putByte(2);
        putInt(fact.type().code());
        putInt(fact.provenance().code());
        putLong(fact.localOrderIdHigh());
        putLong(fact.localOrderIdLow());
        putInt(fact.venueId());
        putInt(fact.instrumentId());
        putLong(fact.sessionGeneration());
        putLong(fact.receiveEpochNanos());
        putLong(fact.receiveMonoNanos());
        putLong(fact.executionIdentityHash());
        putLong(fact.fillQuantity());
        putLong(fact.fillPriceTicks());
        putLong(fact.authoritativeFilled());
        putString(fact.authoritativeState() == null ? "NONE" : fact.authoritativeState().name());
        putInt(fact.reasonCode());
    }

    @SuppressWarnings("ParameterNumber")
    public String finish(
            final FixedDepthOrderBook[] books,
            final ChildOrderTable children,
            final ExecutionGroupTable groups,
            final StrategyRiskLedger ledger,
            final RiskReservationTable reservations,
            final FakeVenue[] venues,
            final DeterministicScheduler scheduler,
            final DeterministicFaultStream faults,
            final int checkpoints,
            final boolean complete,
            final String firstOemsFailure,
            final String failureReason) {
        if (finished != null) return finished;
        putByte(3);
        putBoolean(complete);
        putInt(checkpoints);
        putString(firstOemsFailure);
        putString(failureReason);
        putLong(scheduler.executedEvents());
        putInt(scheduler.size());
        putLong(faults.seed());
        putLong(faults.draws());
        books(books);
        children(children);
        groups(groups);
        ledger(ledger);
        reservations(reservations);
        venues(venues);
        finished = HexFormat.of().formatHex(digest.digest());
        return finished;
    }

    private void books(final FixedDepthOrderBook[] books) {
        putInt(books.length);
        for (FixedDepthOrderBook book : books) {
            putInt(book.venueId());
            putInt(book.instrumentId());
            putInt(book.feedProfileId());
            putString(book.trustState().name());
            putString(book.rejectionReason().name());
            putLong(book.epoch());
            putLong(book.sessionGeneration());
            putLong(book.lastSequence());
            putLong(book.lastReceiveMonoNanos());
            for (BookSide side : BookSide.values()) {
                putString(side.name());
                putInt(book.depth(side));
                for (int level = 0; level < book.depth(side); level++) {
                    putLong(book.priceTicks(side, level));
                    putLong(book.quantityLots(side, level));
                }
            }
        }
    }

    private void children(final ChildOrderTable children) {
        putInt(children.capacity());
        for (int slot = 0; slot < children.capacity(); slot++) {
            final int generation = children.generationAt(slot);
            final ChildOrderState state = children.stateAt(slot);
            putInt(slot);
            putInt(generation);
            putString(state.name());
            if (state == ChildOrderState.FREE) continue;
            putLong(children.localOrderIdHigh(slot, generation));
            putLong(children.localOrderIdLow(slot, generation));
            putInt(children.groupSlot(slot, generation));
            putString(children.role(slot, generation).name());
            putInt(children.venueId(slot, generation));
            putInt(children.instrumentId(slot, generation));
            putString(children.side(slot, generation).name());
            putLong(children.quantity(slot, generation));
            putLong(children.filledQuantity(slot, generation));
            putLong(children.possibleOutstanding(slot, generation));
        }
    }

    private void groups(final ExecutionGroupTable groups) {
        putInt(groups.capacity());
        for (int slot = 0; slot < groups.capacity(); slot++) {
            final int generation = groups.generationAt(slot);
            final ExecutionGroupState state = groups.stateAt(slot);
            putInt(slot);
            putInt(generation);
            putString(state.name());
            if (state == ExecutionGroupState.FREE) continue;
            putInt(groups.strategyId(slot, generation));
            putLong(groups.configurationGeneration(slot, generation));
            putLong(groups.initiationTarget(slot, generation));
            putLong(groups.hedgeTarget(slot, generation));
            putLong(groups.initiationFilled(slot, generation));
            putLong(groups.hedgeRequested(slot, generation));
            putLong(groups.hedgeFilled(slot, generation));
            putLong(groups.maximumImbalance(slot, generation));
            putLong(groups.deadline(slot, generation));
        }
    }

    private void ledger(final StrategyRiskLedger ledger) {
        putInt(ledger.capacity());
        for (int slot = 0; slot < ledger.capacity(); slot++) {
            putInt(slot);
            putInt(ledger.strategyId(slot));
            putLong(ledger.configurationGeneration(slot));
            putLong(ledger.confirmedGross(slot));
            putLong(ledger.pendingGross(slot));
            putLong(ledger.netExposure(slot));
            putLong(ledger.pendingNetExposure(slot));
            putLong(ledger.pendingUnhedgedExposure(slot));
            putLong(ledger.position(slot));
            putLong(ledger.reservedCollateral(slot));
            putLong(ledger.dailyLoss(slot));
            putInt(ledger.activeGroups(slot));
            putInt(ledger.unknownGroups(slot));
        }
    }

    private void reservations(final RiskReservationTable reservations) {
        putInt(reservations.capacity());
        for (int slot = 0; slot < reservations.capacity(); slot++) {
            final int generation = reservations.generationAt(slot);
            final RiskReservationState state = reservations.stateAt(slot);
            putInt(slot);
            putInt(generation);
            putString(state.name());
            if (state == RiskReservationState.FREE) continue;
            putInt(reservations.strategySlot(slot, generation));
            putLong(reservations.configurationGeneration(slot, generation));
            putLong(reservations.remainingGross(slot, generation));
            putLong(reservations.reservedNetExposure(slot, generation));
            putLong(reservations.reservedUnhedgedExposure(slot, generation));
            putLong(reservations.collateral(slot, generation));
            putLong(reservations.remainingHedgeClaims(slot, generation));
            putLong(reservations.expiryMonoNanos(slot, generation));
        }
    }

    private void venues(final FakeVenue[] venues) {
        putInt(venues.length);
        for (FakeVenue venue : venues) {
            putInt(venue.venueId());
            putLong(venue.commandsReceived());
            putLong(venue.factsPublished());
            putLong(venue.publicationFailures());
            putLong(venue.scheduleFailures());
            putInt(venue.capacity());
            for (int slot = 0; slot < venue.capacity(); slot++) {
                putInt(slot);
                putString(venue.stateAt(slot).name());
                putLong(venue.idHighAt(slot));
                putLong(venue.idLowAt(slot));
                putLong(venue.filledAt(slot));
            }
        }
    }

    private void putString(final String value) {
        if (value == null) throw new IllegalArgumentException("digest strings are required");
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        putInt(bytes.length);
        digest.update(bytes);
    }

    private void putBoolean(final boolean value) {
        putByte(value ? 1 : 0);
    }

    private void putByte(final int value) {
        digest.update((byte) value);
    }

    private void putInt(final int value) {
        scalar.clear();
        scalar.putInt(value);
        digest.update(scalar.array(), 0, Integer.BYTES);
    }

    private void putLong(final long value) {
        scalar.clear();
        scalar.putLong(value);
        digest.update(scalar.array(), 0, Long.BYTES);
    }

    private void requireOpen() {
        if (finished != null) throw new IllegalStateException("digest is finished");
    }
}
