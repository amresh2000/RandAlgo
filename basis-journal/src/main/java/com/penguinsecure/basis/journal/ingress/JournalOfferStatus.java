package com.penguinsecure.basis.journal.ingress;

/** Explicit result of a nonblocking journal admission attempt. */
public enum JournalOfferStatus {
    ACCEPTED,
    ACCEPTED_LOSSY,
    NORMAL_LIMIT_REACHED,
    CAPACITY_EXHAUSTED,
    OVERSIZE,
    INVALID_CLASSIFICATION,
    STALE_PRODUCER_SEQUENCE,
    LOSSY_DROPPED
}
