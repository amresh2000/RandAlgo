package com.penguinsecure.basis.app.certification;

import java.util.List;
import java.util.regex.Pattern;

/** Evidence captured for one uninterrupted soak window. */
public record SoakEvidence(
        long durationSeconds,
        EvidenceStatus status,
        boolean allocationAndJfr,
        boolean queue,
        boolean cpu,
        boolean memory,
        boolean reconnect,
        boolean latency,
        List<String> artifactSha256) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public SoakEvidence {
        if (durationSeconds <= 0 || status == null) throw new IllegalArgumentException();
        artifactSha256 = List.copyOf(artifactSha256);
        if (artifactSha256.stream().anyMatch(value -> !SHA256.matcher(value).matches())) {
            throw new IllegalArgumentException("invalid artifact SHA-256");
        }
        final boolean reportsComplete =
                allocationAndJfr && queue && cpu && memory && reconnect && latency;
        if (status == EvidenceStatus.PASSED && (!reportsComplete || artifactSha256.isEmpty())) {
            throw new IllegalArgumentException("passed soak requires every report");
        }
    }

    public boolean complete() {
        return allocationAndJfr && queue && cpu && memory && reconnect && latency;
    }
}
