package com.penguinsecure.basis.app.certification;

import java.util.List;
import java.util.regex.Pattern;

/** Sanitized evidence references for one acceptance criterion. */
public record AcceptanceEvidence(
        AcceptanceCriterion criterion, EvidenceStatus status, List<String> artifactSha256) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public AcceptanceEvidence {
        if (criterion == null || status == null) throw new NullPointerException();
        artifactSha256 = List.copyOf(artifactSha256);
        if (artifactSha256.stream().anyMatch(value -> !SHA256.matcher(value).matches())) {
            throw new IllegalArgumentException("invalid artifact SHA-256");
        }
        if (status == EvidenceStatus.PASSED && artifactSha256.isEmpty()) {
            throw new IllegalArgumentException("passed criterion requires evidence");
        }
    }
}
