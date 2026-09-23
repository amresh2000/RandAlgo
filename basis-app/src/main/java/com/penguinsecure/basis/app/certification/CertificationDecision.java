package com.penguinsecure.basis.app.certification;

/** Aggregate result deliberately separates absent evidence from observed failure. */
public enum CertificationDecision {
    INCOMPLETE,
    FAILED,
    CERTIFIED
}
