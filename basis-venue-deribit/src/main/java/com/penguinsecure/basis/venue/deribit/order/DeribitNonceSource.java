package com.penguinsecure.basis.venue.deribit.order;

/** Fills a caller-owned buffer with a fresh safe-ASCII authentication nonce. */
@FunctionalInterface
public interface DeribitNonceSource {
    void next(StringBuilder destination);
}
