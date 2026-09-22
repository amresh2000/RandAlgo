package com.penguinsecure.basis.core.risk;

/** Result of a generation-fenced kill/reset update. */
public enum KillUpdateStatus {
    APPLIED,
    IDEMPOTENT,
    STALE_GENERATION,
    INVALID_ARGUMENT
}
