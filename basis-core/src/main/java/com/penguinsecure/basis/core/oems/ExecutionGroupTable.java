package com.penguinsecure.basis.core.oems;

/** Structure-of-arrays execution groups isolated by strategy configuration generation. */
public final class ExecutionGroupTable {
    private final int[] generations;
    private final int[] nextFree;
    private final int[] strategyIds;
    private final long[] configurationGenerations;
    private final int[] reservationSlots;
    private final int[] reservationGenerations;
    private final long[] initiationTargets;
    private final long[] hedgeTargets;
    private final long[] initiationFilled;
    private final long[] hedgeRequested;
    private final long[] hedgeFilled;
    private final long[] maximumImbalances;
    private final long[] deadlines;
    private final ExecutionGroupState[] states;
    private int freeHead;

    public ExecutionGroupTable(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        generations = new int[capacity];
        nextFree = new int[capacity];
        strategyIds = new int[capacity];
        configurationGenerations = new long[capacity];
        reservationSlots = new int[capacity];
        reservationGenerations = new int[capacity];
        initiationTargets = new long[capacity];
        hedgeTargets = new long[capacity];
        initiationFilled = new long[capacity];
        hedgeRequested = new long[capacity];
        hedgeFilled = new long[capacity];
        maximumImbalances = new long[capacity];
        deadlines = new long[capacity];
        states = new ExecutionGroupState[capacity];
        for (int slot = 0; slot < capacity; slot++) {
            nextFree[slot] = slot + 1;
            states[slot] = ExecutionGroupState.FREE;
        }
        nextFree[capacity - 1] = -1;
    }

    @SuppressWarnings("ParameterNumber")
    public OemsStatus create(
            final int strategyId,
            final long configurationGeneration,
            final int reservationSlot,
            final int reservationGeneration,
            final long initiationTarget,
            final long hedgeTarget,
            final long maximumImbalance,
            final long deadline,
            final MutableSlotHandle destination) {
        if (strategyId <= 0
                || configurationGeneration <= 0
                || reservationSlot < 0
                || reservationGeneration <= 0
                || initiationTarget <= 0
                || hedgeTarget <= 0
                || maximumImbalance <= 0
                || deadline <= 0
                || destination == null) {
            return OemsStatus.INVALID_STATE;
        }
        if (freeHead < 0) return OemsStatus.CAPACITY_EXHAUSTED;
        final int slot = freeHead;
        freeHead = nextFree[slot];
        int generation = generations[slot] + 1;
        if (generation == 0) generation = 1;
        generations[slot] = generation;
        strategyIds[slot] = strategyId;
        configurationGenerations[slot] = configurationGeneration;
        reservationSlots[slot] = reservationSlot;
        reservationGenerations[slot] = reservationGeneration;
        initiationTargets[slot] = initiationTarget;
        hedgeTargets[slot] = hedgeTarget;
        initiationFilled[slot] = 0;
        hedgeRequested[slot] = 0;
        hedgeFilled[slot] = 0;
        maximumImbalances[slot] = maximumImbalance;
        deadlines[slot] = deadline;
        states[slot] = ExecutionGroupState.RESERVED;
        destination.set(slot, generation);
        return OemsStatus.OK;
    }

    public OemsStatus markInitiating(final int slot, final int generation) {
        return transition(
                slot, generation, ExecutionGroupState.RESERVED, ExecutionGroupState.INITIATING);
    }

    public OemsStatus recordInitiationFill(
            final int slot, final int generation, final long cumulativeFill) {
        if (!valid(slot, generation)
                || cumulativeFill < initiationFilled[slot]
                || cumulativeFill > initiationTargets[slot]) return OemsStatus.INVALID_STATE;
        initiationFilled[slot] = cumulativeFill;
        states[slot] = ExecutionGroupState.HEDGING;
        return OemsStatus.OK;
    }

    public OemsStatus recordHedgeRequested(
            final int slot, final int generation, final long cumulativeRequested) {
        if (!valid(slot, generation)
                || cumulativeRequested < hedgeRequested[slot]
                || cumulativeRequested > hedgeTargets[slot]) return OemsStatus.INVALID_STATE;
        hedgeRequested[slot] = cumulativeRequested;
        return OemsStatus.OK;
    }

    public OemsStatus recordHedgeFill(
            final int slot, final int generation, final long cumulativeFill) {
        if (!valid(slot, generation)
                || cumulativeFill < hedgeFilled[slot]
                || cumulativeFill > hedgeRequested[slot]) return OemsStatus.INVALID_STATE;
        hedgeFilled[slot] = cumulativeFill;
        if (hedgeFilled[slot] == hedgeTargets[slot]
                && initiationFilled[slot] == initiationTargets[slot]) {
            states[slot] = ExecutionGroupState.BALANCED;
        }
        return OemsStatus.OK;
    }

    public OemsStatus markUnknown(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == ExecutionGroupState.UNKNOWN) return OemsStatus.DUPLICATE;
        if (states[slot] == ExecutionGroupState.COMPLETE) return OemsStatus.INVALID_STATE;
        states[slot] = ExecutionGroupState.UNKNOWN;
        return OemsStatus.OK;
    }

    public OemsStatus markUnwinding(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        states[slot] = ExecutionGroupState.UNWINDING;
        return OemsStatus.OK;
    }

    public OemsStatus fail(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        states[slot] = ExecutionGroupState.FAILED;
        return OemsStatus.OK;
    }

    public OemsStatus completeBalanced(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] != ExecutionGroupState.BALANCED) return OemsStatus.INVALID_STATE;
        states[slot] = ExecutionGroupState.COMPLETE;
        return OemsStatus.OK;
    }

    public ExecutionGroupState state(final int slot, final int generation) {
        return valid(slot, generation) ? states[slot] : ExecutionGroupState.FREE;
    }

    public long initiationTarget(final int slot, final int generation) {
        return valid(slot, generation) ? initiationTargets[slot] : 0;
    }

    public long hedgeTarget(final int slot, final int generation) {
        return valid(slot, generation) ? hedgeTargets[slot] : 0;
    }

    public long initiationFilled(final int slot, final int generation) {
        return valid(slot, generation) ? initiationFilled[slot] : 0;
    }

    public long hedgeRequested(final int slot, final int generation) {
        return valid(slot, generation) ? hedgeRequested[slot] : 0;
    }

    public long hedgeFilled(final int slot, final int generation) {
        return valid(slot, generation) ? hedgeFilled[slot] : 0;
    }

    public long maximumImbalance(final int slot, final int generation) {
        return valid(slot, generation) ? maximumImbalances[slot] : 0;
    }

    public int reservationSlot(final int slot, final int generation) {
        return valid(slot, generation) ? reservationSlots[slot] : -1;
    }

    public int reservationGeneration(final int slot, final int generation) {
        return valid(slot, generation) ? reservationGenerations[slot] : 0;
    }

    public int strategyId(final int slot, final int generation) {
        return valid(slot, generation) ? strategyIds[slot] : 0;
    }

    public long configurationGeneration(final int slot, final int generation) {
        return valid(slot, generation) ? configurationGenerations[slot] : 0;
    }

    public int capacity() {
        return states.length;
    }

    public int generationAt(final int slot) {
        return slot >= 0 && slot < states.length ? generations[slot] : 0;
    }

    public ExecutionGroupState stateAt(final int slot) {
        return slot >= 0 && slot < states.length ? states[slot] : ExecutionGroupState.FREE;
    }

    public long deadline(final int slot, final int generation) {
        return valid(slot, generation) ? deadlines[slot] : 0;
    }

    public OemsStatus abandonReserved(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] != ExecutionGroupState.RESERVED) return OemsStatus.INVALID_STATE;
        states[slot] = ExecutionGroupState.FREE;
        nextFree[slot] = freeHead;
        freeHead = slot;
        return OemsStatus.OK;
    }

    public OemsStatus releaseTerminal(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] != ExecutionGroupState.COMPLETE
                && states[slot] != ExecutionGroupState.FAILED) return OemsStatus.INVALID_STATE;
        states[slot] = ExecutionGroupState.FREE;
        nextFree[slot] = freeHead;
        freeHead = slot;
        return OemsStatus.OK;
    }

    private OemsStatus transition(
            final int slot,
            final int generation,
            final ExecutionGroupState expected,
            final ExecutionGroupState next) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == next) return OemsStatus.DUPLICATE;
        if (states[slot] != expected) return OemsStatus.INVALID_STATE;
        states[slot] = next;
        return OemsStatus.OK;
    }

    private boolean valid(final int slot, final int generation) {
        return slot >= 0
                && slot < states.length
                && generation != 0
                && generations[slot] == generation
                && states[slot] != ExecutionGroupState.FREE;
    }
}
