package com.penguinsecure.basis.app.operator;

public enum OperatorCommandStatus {
    APPLIED,
    ACCEPTED,
    DUPLICATE,
    UNAUTHENTICATED,
    UNAUTHORIZED,
    EXPIRED,
    STALE_GENERATION,
    NOT_READY,
    AUDIT_FAILED,
    ACTION_FAILED,
    CAPACITY_EXHAUSTED,
    INVALID
}
