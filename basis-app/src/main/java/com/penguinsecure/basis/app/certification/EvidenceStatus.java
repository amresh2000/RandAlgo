package com.penguinsecure.basis.app.certification;

/** Explicit evidence status; absence is never equivalent to a pass. */
public enum EvidenceStatus {
    NOT_RUN,
    BLOCKED,
    FAILED,
    PASSED
}
