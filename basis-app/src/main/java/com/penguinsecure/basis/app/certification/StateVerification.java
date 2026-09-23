package com.penguinsecure.basis.app.certification;

/** Required post-scenario agreement with venue and journal truth. */
public record StateVerification(
        boolean positions,
        boolean balances,
        boolean fills,
        boolean fees,
        boolean openOrders,
        boolean reservations,
        boolean journal) {
    public boolean complete() {
        return positions && balances && fills && fees && openOrders && reservations && journal;
    }
}
