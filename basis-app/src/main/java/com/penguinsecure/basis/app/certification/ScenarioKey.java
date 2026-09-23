package com.penguinsecure.basis.app.certification;

/** Unique required execution in the certification matrix. */
public record ScenarioKey(CertificationScenario scenario, CertificationVenue venue) {
    public ScenarioKey {
        if (scenario == null || venue == null || !scenario.appliesTo(venue)) {
            throw new IllegalArgumentException("scenario does not apply to venue");
        }
    }
}
