package com.penguinsecure.basis.venue.api.session;

/** Saturating exponential reconnect policy with deterministic bounded jitter input. */
public record ReconnectPolicy(
        long initialDelayNanos,
        long maximumDelayNanos,
        int maximumExponent,
        int jitterBasisPoints) {
    public ReconnectPolicy {
        if (initialDelayNanos <= 0 || maximumDelayNanos < initialDelayNanos) {
            throw new IllegalArgumentException("invalid reconnect delays");
        }
        if (maximumExponent < 0
                || maximumExponent > 62
                || jitterBasisPoints < 0
                || jitterBasisPoints > 10_000) {
            throw new IllegalArgumentException("invalid reconnect exponent or jitter");
        }
    }

    public long delayNanos(final int attempt, final int signedJitterBasisPoints) {
        final int exponent = Math.min(Math.max(attempt, 0), maximumExponent);
        long delay = initialDelayNanos;
        for (int i = 0; i < exponent && delay < maximumDelayNanos; i++) {
            delay = delay > maximumDelayNanos / 2 ? maximumDelayNanos : delay * 2;
        }
        delay = Math.min(delay, maximumDelayNanos);
        final int boundedJitter =
                Math.min(jitterBasisPoints, Math.max(-jitterBasisPoints, signedJitterBasisPoints));
        final long adjustment =
                (delay / 10_000) * boundedJitter + ((delay % 10_000) * boundedJitter) / 10_000;
        if (adjustment > 0 && adjustment > maximumDelayNanos - delay) return maximumDelayNanos;
        return Math.max(0, delay + adjustment);
    }
}
