package com.penguinsecure.basis.core.oems;

/** Reusable generation-fenced slot reference. */
public final class MutableSlotHandle {
    private int slot = -1;
    private int generation;

    public int slot() {
        return slot;
    }

    public int generation() {
        return generation;
    }

    public MutableSlotHandle clear() {
        slot = -1;
        generation = 0;
        return this;
    }

    MutableSlotHandle set(final int newSlot, final int newGeneration) {
        slot = newSlot;
        generation = newGeneration;
        return this;
    }
}
