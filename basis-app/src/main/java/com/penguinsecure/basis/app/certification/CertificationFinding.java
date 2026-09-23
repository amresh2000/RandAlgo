package com.penguinsecure.basis.app.certification;

import java.util.regex.Pattern;

/** Bounded, non-sensitive finding metadata. */
public record CertificationFinding(String id, int severity, boolean resolved) {
    private static final Pattern ID = Pattern.compile("[A-Z0-9_-]{1,64}");

    public CertificationFinding {
        if (id == null || !ID.matcher(id).matches() || severity < 1 || severity > 4) {
            throw new IllegalArgumentException("invalid finding");
        }
    }
}
