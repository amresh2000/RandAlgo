package com.penguinsecure.basis.core.deadline;

/** Caller-owned handle storage; generation prevents stale cancellation after slot reuse. */
public final class MutableDeadlineHandle {
    private int slot = -1;
    private int generation;

    public int slot() {
        return slot;
    }

    public int generation() {
        return generation;
    }

    MutableDeadlineHandle set(int slot, int generation) {
        this.slot = slot;
        this.generation = generation;
        return this;
    }
}
