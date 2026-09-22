package com.penguinsecure.basis.strategy.api.pricing;

/** Version, provenance, confidence, and freshness evidence for one economic input. */
public record InputMetadata(
        int sourceId,
        long generation,
        long effectiveEpochNanos,
        long receiveMonoNanos,
        long expiryEpochNanos,
        long maximumAgeNanos,
        int provenanceId,
        int confidencePartsPerMillion) {

    public InputMetadata {
        if (sourceId <= 0
                || generation <= 0
                || effectiveEpochNanos <= 0
                || receiveMonoNanos <= 0
                || expiryEpochNanos <= effectiveEpochNanos
                || maximumAgeNanos <= 0
                || provenanceId <= 0
                || confidencePartsPerMillion <= 0
                || confidencePartsPerMillion > 1_000_000) {
            throw new IllegalArgumentException("invalid economic input metadata");
        }
    }

    public boolean isUsable(
            final int expectedSourceId,
            final long decisionEpochNanos,
            final long decisionMonoNanos) {
        return sourceId == expectedSourceId
                && decisionEpochNanos >= effectiveEpochNanos
                && decisionEpochNanos < expiryEpochNanos
                && decisionMonoNanos >= receiveMonoNanos
                && decisionMonoNanos - receiveMonoNanos <= maximumAgeNanos;
    }
}
