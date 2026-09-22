package com.penguinsecure.basis.core.risk;

/** Caller-owned generation-fenced reservation reference. */
public final class MutableRiskReservationHandle {
    private int slot = -1;
    private int generation;

    public int slot() {
        return slot;
    }

    public int generation() {
        return generation;
    }

    public MutableRiskReservationHandle clear() {
        slot = -1;
        generation = 0;
        return this;
    }

    MutableRiskReservationHandle set(final int newSlot, final int newGeneration) {
        slot = newSlot;
        generation = newGeneration;
        return this;
    }
}
