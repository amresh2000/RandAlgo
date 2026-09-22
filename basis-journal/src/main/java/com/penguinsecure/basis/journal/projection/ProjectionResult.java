package com.penguinsecure.basis.journal.projection;

/** Cold projector result without connection-string leakage. */
public enum ProjectionResult {
    COMMITTED,
    RETRYABLE_FAILURE,
    INVALID_EVENT,
    UNAUTHORIZED_REBUILD
}
