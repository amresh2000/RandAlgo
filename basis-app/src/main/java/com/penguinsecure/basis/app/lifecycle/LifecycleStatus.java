package com.penguinsecure.basis.app.lifecycle;

public enum LifecycleStatus {
    APPLIED,
    IDEMPOTENT,
    NOT_READY,
    STALE_GENERATION,
    INVALID_STATE,
    INVALID_ARGUMENT
}
