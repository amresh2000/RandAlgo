package com.penguinsecure.basis.core.recovery;

/** Result of validating a fresh recovered aggregate. */
public enum RecoveryValidationStatus {
    VALID,
    INVALID_CAPACITY,
    INVALID_REFERENCE,
    INVALID_ARITHMETIC,
    INVALID_GENERATION
}
