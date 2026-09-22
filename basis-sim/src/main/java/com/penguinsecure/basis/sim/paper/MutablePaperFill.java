package com.penguinsecure.basis.sim.paper;

/** Caller-owned conservative paper-fill result. */
public final class MutablePaperFill {
    private boolean proven;
    private long quantity;
    private long priceTicks;

    public MutablePaperFill clear() {
        proven = false;
        quantity = 0;
        priceTicks = 0;
        return this;
    }

    MutablePaperFill set(final long newQuantity, final long newPriceTicks) {
        proven = newQuantity > 0 && newPriceTicks > 0;
        quantity = newQuantity;
        priceTicks = newPriceTicks;
        return this;
    }

    public boolean proven() {
        return proven;
    }

    public long quantity() {
        return quantity;
    }

    public long priceTicks() {
        return priceTicks;
    }
}
