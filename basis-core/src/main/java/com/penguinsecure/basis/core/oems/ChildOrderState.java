package com.penguinsecure.basis.core.oems;

/** Child-order lifecycle including explicit ambiguity states. */
public enum ChildOrderState {
    FREE,
    CREATED,
    SEND_PENDING,
    SENT,
    ACKNOWLEDGED,
    WORKING,
    PARTIALLY_FILLED,
    FILLED,
    CANCEL_PENDING,
    CANCELLED,
    REJECTED,
    UNKNOWN,
    RECONCILING,
    FAULTED
}
