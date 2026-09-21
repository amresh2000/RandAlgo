package com.penguinsecure.basis.core.risk;

/** Lifecycle of capacity held for one execution group. */
public enum RiskReservationState {
    FREE,
    RESERVED,
    SENT,
    UNKNOWN,
    RECONCILING
}
