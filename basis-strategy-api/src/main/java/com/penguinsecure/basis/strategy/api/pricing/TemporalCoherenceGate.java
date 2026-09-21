package com.penguinsecure.basis.strategy.api.pricing;

import com.penguinsecure.basis.core.book.BookTrustState;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;

/** Same-clock per-leg age and cross-leg receive-skew gate. */
public final class TemporalCoherenceGate {
    private TemporalCoherenceGate() {}

    public static PricingStatus evaluate(
            final FixedDepthOrderBook first,
            final FixedDepthOrderBook second,
            final long firstMaximumAgeNanos,
            final long secondMaximumAgeNanos,
            final long maximumSkewNanos,
            final long decisionMonoNanos,
            final MutableTemporalEvidence evidence) {
        if (first == null
                || second == null
                || evidence == null
                || firstMaximumAgeNanos <= 0
                || secondMaximumAgeNanos <= 0
                || maximumSkewNanos <= 0
                || decisionMonoNanos <= 0) return PricingStatus.INVALID_ARGUMENT;
        if (first.trustState() != BookTrustState.TRUSTED
                || second.trustState() != BookTrustState.TRUSTED) {
            return PricingStatus.BOOK_UNTRUSTED;
        }
        final long firstReceive = first.lastReceiveMonoNanos();
        final long secondReceive = second.lastReceiveMonoNanos();
        if (firstReceive <= 0
                || secondReceive <= 0
                || decisionMonoNanos < firstReceive
                || decisionMonoNanos < secondReceive) return PricingStatus.BOOK_STALE;
        final long firstAge = decisionMonoNanos - firstReceive;
        final long secondAge = decisionMonoNanos - secondReceive;
        final long skew =
                firstReceive >= secondReceive
                        ? firstReceive - secondReceive
                        : secondReceive - firstReceive;
        evidence.set(firstAge, secondAge, skew);
        if (firstAge > firstMaximumAgeNanos || secondAge > secondMaximumAgeNanos) {
            return PricingStatus.BOOK_STALE;
        }
        return skew > maximumSkewNanos
                ? PricingStatus.PRICE_NOT_TEMPORALLY_COHERENT
                : PricingStatus.OPPORTUNITY;
    }
}
