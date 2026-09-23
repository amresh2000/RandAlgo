package com.penguinsecure.basis.app.observability;

import java.util.Arrays;

/** Fixed logarithmic histogram for non-negative nanosecond stages. */
public final class StageLatencyHistogram {
    private final long[] buckets = new long[64];
    private long count, maximum;

    public void record(final long nanos) {
        if (nanos < 0) return;
        final int bucket = nanos == 0 ? 0 : 64 - Long.numberOfLeadingZeros(nanos);
        buckets[Math.min(bucket, buckets.length - 1)]++;
        count++;
        if (nanos > maximum) maximum = nanos;
    }

    public long percentile(final int partsPerMillion) {
        if (count == 0 || partsPerMillion < 0 || partsPerMillion > 1_000_000) return 0;
        final long quotient = count / 1_000_000;
        final long remainder = count % 1_000_000;
        final long rank =
                Math.max(
                        1,
                        quotient * partsPerMillion
                                + (remainder * partsPerMillion + 999_999) / 1_000_000);
        long seen = 0;
        for (int index = 0; index < buckets.length; index++) {
            seen += buckets[index];
            if (seen >= rank) {
                if (index == 0) return 0;
                return index == buckets.length - 1 ? Long.MAX_VALUE : (1L << index) - 1;
            }
        }
        return maximum;
    }

    public long count() {
        return count;
    }

    public long maximum() {
        return maximum;
    }

    public void reset() {
        Arrays.fill(buckets, 0);
        count = 0;
        maximum = 0;
    }
}
