package com.penguinsecure.basis.core.oems;

/** Coordinated multi-leg execution lifecycle. */
public enum ExecutionGroupState {
    FREE,
    PLANNED,
    RESERVED,
    INITIATING,
    HEDGING,
    BALANCED,
    UNWINDING,
    UNKNOWN,
    FAILED,
    COMPLETE
}
