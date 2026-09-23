package com.penguinsecure.basis.app.certification;

import java.util.List;

/** Deterministic gate output with bounded reason codes. */
public record CertificationGateResult(CertificationDecision decision, List<String> reasonCodes) {
    public CertificationGateResult {
        if (decision == null) throw new NullPointerException("decision is required");
        reasonCodes = List.copyOf(reasonCodes);
    }
}
