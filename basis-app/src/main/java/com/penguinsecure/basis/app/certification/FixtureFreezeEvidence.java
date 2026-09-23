package com.penguinsecure.basis.app.certification;

import java.util.regex.Pattern;

/** Review approval for one venue's sanitized, content-addressed wire fixture set. */
public record FixtureFreezeEvidence(
        CertificationVenue venue, EvidenceStatus status, String manifestSha256) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public FixtureFreezeEvidence {
        if (venue == null || venue == CertificationVenue.CELL || status == null) {
            throw new IllegalArgumentException("venue fixture evidence is required");
        }
        if (manifestSha256 == null || !SHA256.matcher(manifestSha256).matches()) {
            throw new IllegalArgumentException("invalid manifest SHA-256");
        }
    }
}
