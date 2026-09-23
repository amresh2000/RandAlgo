package com.penguinsecure.basis.app.certification;

import java.util.ArrayList;
import java.util.List;

/** Canonical expansion of scenarios into required venue/cell executions. */
public final class CertificationMatrix {
    private static final List<ScenarioKey> REQUIRED = build();

    private CertificationMatrix() {}

    public static List<ScenarioKey> requiredRuns() {
        return REQUIRED;
    }

    private static List<ScenarioKey> build() {
        final List<ScenarioKey> required = new ArrayList<>();
        for (CertificationScenario scenario : CertificationScenario.values()) {
            for (CertificationVenue venue : CertificationVenue.values()) {
                if (scenario.appliesTo(venue)) required.add(new ScenarioKey(scenario, venue));
            }
        }
        return List.copyOf(required);
    }
}
