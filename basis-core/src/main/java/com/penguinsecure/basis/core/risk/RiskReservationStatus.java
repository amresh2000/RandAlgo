package com.penguinsecure.basis.core.risk;

/** Result of an expected reservation-table operation. */
public enum RiskReservationStatus {
    OK,
    CAPACITY_EXHAUSTED,
    INVALID_HANDLE,
    INVALID_STATE,
    NUMERIC_FAILURE
}
