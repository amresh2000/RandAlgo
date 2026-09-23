package com.penguinsecure.basis.app.certification;

import java.util.List;
import java.util.regex.Pattern;

/** Sanitized evidence for one scenario execution; raw payloads and secrets are excluded. */
public record ScenarioEvidence(
        ScenarioKey key,
        EvidenceStatus status,
        long startedEpochMillis,
        long completedEpochMillis,
        StateVerification stateVerification,
        List<String> artifactSha256,
        String reasonCode) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REASON = Pattern.compile("[A-Z0-9_]{1,64}");

    public ScenarioEvidence {
        if (key == null || status == null || stateVerification == null) {
            throw new NullPointerException("scenario evidence fields are required");
        }
        if (startedEpochMillis < 0 || completedEpochMillis < startedEpochMillis) {
            throw new IllegalArgumentException("invalid scenario timestamps");
        }
        artifactSha256 = List.copyOf(artifactSha256);
        if (artifactSha256.stream().anyMatch(value -> !SHA256.matcher(value).matches())) {
            throw new IllegalArgumentException("invalid artifact SHA-256");
        }
        if (reasonCode == null || !REASON.matcher(reasonCode).matches()) {
            throw new IllegalArgumentException("reason code must be sanitized and bounded");
        }
        if (status == EvidenceStatus.PASSED
                && (startedEpochMillis == 0
                        || !stateVerification.complete()
                        || artifactSha256.isEmpty())) {
            throw new IllegalArgumentException(
                    "passed scenario requires timestamps, state verification, and an artifact");
        }
    }
}
