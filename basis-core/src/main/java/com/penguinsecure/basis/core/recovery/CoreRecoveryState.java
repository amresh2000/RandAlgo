package com.penguinsecure.basis.core.recovery;

import com.penguinsecure.basis.core.oems.ChildOrderTable;
import com.penguinsecure.basis.core.oems.ExecutionGroupTable;
import com.penguinsecure.basis.core.risk.KillHierarchy;
import com.penguinsecure.basis.core.risk.RiskReservationTable;
import com.penguinsecure.basis.core.risk.StrategyRiskLedger;

/** Fresh bounded core aggregate populated by snapshot load and replay before publication. */
public final class CoreRecoveryState {
    private final StrategyRiskLedger ledger;
    private final RiskReservationTable reservations;
    private final ExecutionGroupTable groups;
    private final ChildOrderTable children;
    private final KillHierarchy kills;
    private long nextJournalSequence;
    private long appliedReplayEvents;

    public CoreRecoveryState(
            final int strategyCapacity,
            final int reservationCapacity,
            final int groupCapacity,
            final int childCapacity,
            final int executionCapacity,
            final int maximumKillScopeId) {
        ledger = new StrategyRiskLedger(strategyCapacity);
        reservations = new RiskReservationTable(reservationCapacity, ledger);
        groups = new ExecutionGroupTable(groupCapacity);
        children = new ChildOrderTable(childCapacity, executionCapacity);
        kills = new KillHierarchy(maximumKillScopeId);
    }

    public StrategyRiskLedger ledger() {
        return ledger;
    }

    public RiskReservationTable reservations() {
        return reservations;
    }

    public ExecutionGroupTable groups() {
        return groups;
    }

    public ChildOrderTable children() {
        return children;
    }

    public KillHierarchy kills() {
        return kills;
    }

    public long nextJournalSequence() {
        return nextJournalSequence;
    }

    public long appliedReplayEvents() {
        return appliedReplayEvents;
    }

    public void restoreReplayProgress(
            final long nextJournalSequence, final long appliedReplayEvents) {
        if (nextJournalSequence < 0 || appliedReplayEvents < 0) {
            throw new IllegalArgumentException("invalid replay progress");
        }
        this.nextJournalSequence = nextJournalSequence;
        this.appliedReplayEvents = appliedReplayEvents;
    }

    public void eventApplied(final long eventSequence) {
        if (eventSequence != nextJournalSequence + 1) {
            throw new IllegalStateException("non-contiguous journal event sequence");
        }
        nextJournalSequence = eventSequence;
        appliedReplayEvents++;
    }
}
