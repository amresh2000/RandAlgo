package com.penguinsecure.basis.app.certification;

import java.util.List;

/** Immutable input to the Phase 12 release gate. */
public record CertificationBundle(
        String buildRevision,
        List<ScenarioEvidence> scenarios,
        List<AcceptanceEvidence> acceptanceCriteria,
        List<SoakEvidence> soaks,
        List<FixtureFreezeEvidence> fixtures,
        List<CertificationFinding> findings,
        boolean testnetLimitationsDocumented) {
    public CertificationBundle {
        if (buildRevision == null || !buildRevision.matches("[0-9a-f]{7,64}")) {
            throw new IllegalArgumentException("build revision must be a git object ID");
        }
        scenarios = List.copyOf(scenarios);
        acceptanceCriteria = List.copyOf(acceptanceCriteria);
        soaks = List.copyOf(soaks);
        fixtures = List.copyOf(fixtures);
        findings = List.copyOf(findings);
    }
}
