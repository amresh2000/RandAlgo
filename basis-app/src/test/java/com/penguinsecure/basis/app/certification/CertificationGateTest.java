package com.penguinsecure.basis.app.certification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class CertificationGateTest {
    private static final String HASH = "a".repeat(64);
    private static final StateVerification VERIFIED =
            new StateVerification(true, true, true, true, true, true, true);

    @Test
    void canonicalMatrixContainsEveryRequiredVenueAndCellRun() {
        assertEquals(30, CertificationMatrix.requiredRuns().size());
        assertTrue(
                CertificationMatrix.requiredRuns()
                        .contains(
                                new ScenarioKey(
                                        CertificationScenario.TOKEN_EXPIRY,
                                        CertificationVenue.DERIBIT)));
        assertFalse(
                CertificationMatrix.requiredRuns().stream()
                        .anyMatch(
                                key ->
                                        key.scenario() == CertificationScenario.TOKEN_EXPIRY
                                                && key.venue() == CertificationVenue.BYBIT));
        assertTrue(
                CertificationMatrix.requiredRuns()
                        .contains(
                                new ScenarioKey(
                                        CertificationScenario.FRESH_BOOKS_HIGH_SKEW,
                                        CertificationVenue.CELL)));
    }

    @Test
    void completeEvidenceCertifies() {
        CertificationGateResult result = new CertificationGate().evaluate(completeBundle());

        assertEquals(CertificationDecision.CERTIFIED, result.decision());
        assertTrue(result.reasonCodes().isEmpty());
    }

    @Test
    void absentLiveEvidenceIsIncompleteRatherThanCertified() {
        CertificationBundle bundle =
                new CertificationBundle(
                        "4422a24", List.of(), List.of(), List.of(), List.of(), List.of(), false);

        CertificationGateResult result = new CertificationGate().evaluate(bundle);

        assertEquals(CertificationDecision.INCOMPLETE, result.decision());
        assertTrue(result.reasonCodes().contains("MISSING_SCENARIO_ACCEPTED_BYBIT"));
        assertTrue(result.reasonCodes().contains("MISSING_SOAK_24H"));
        assertTrue(result.reasonCodes().contains("MISSING_SOAK_7D"));
        assertTrue(result.reasonCodes().contains("MISSING_TESTNET_LIMITATIONS"));
    }

    @Test
    void observedFailureAndSevereFindingFailCertification() {
        CertificationBundle complete = completeBundle();
        List<ScenarioEvidence> scenarios = new ArrayList<>(complete.scenarios());
        ScenarioKey failedKey = scenarios.getFirst().key();
        scenarios.set(
                0,
                new ScenarioEvidence(
                        failedKey,
                        EvidenceStatus.FAILED,
                        1,
                        2,
                        VERIFIED,
                        List.of(HASH),
                        "STATE_MISMATCH"));
        CertificationBundle failed =
                new CertificationBundle(
                        complete.buildRevision(),
                        scenarios,
                        complete.acceptanceCriteria(),
                        complete.soaks(),
                        complete.fixtures(),
                        List.of(new CertificationFinding("OMS-1", 1, false)),
                        true);

        CertificationGateResult result = new CertificationGate().evaluate(failed);

        assertEquals(CertificationDecision.FAILED, result.decision());
        assertTrue(result.reasonCodes().stream().anyMatch(code -> code.startsWith("SCENARIO_")));
        assertTrue(result.reasonCodes().contains("UNRESOLVED_SEV1_OMS-1"));
    }

    @Test
    void passedScenarioRequiresEveryStateCheckAndArtifact() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScenarioEvidence(
                                CertificationMatrix.requiredRuns().getFirst(),
                                EvidenceStatus.PASSED,
                                1,
                                2,
                                new StateVerification(true, true, true, true, true, true, false),
                                List.of(HASH),
                                "OK"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScenarioEvidence(
                                CertificationMatrix.requiredRuns().getFirst(),
                                EvidenceStatus.PASSED,
                                1,
                                2,
                                VERIFIED,
                                List.of(),
                                "OK"));
    }

    @Test
    void sevenDayRunCannotSubstituteForSeparateTwentyFourHourGate() {
        CertificationBundle complete = completeBundle();
        CertificationBundle missingDay =
                new CertificationBundle(
                        complete.buildRevision(),
                        complete.scenarios(),
                        complete.acceptanceCriteria(),
                        List.of(complete.soaks().getLast()),
                        complete.fixtures(),
                        complete.findings(),
                        true);

        CertificationGateResult result = new CertificationGate().evaluate(missingDay);

        assertEquals(CertificationDecision.INCOMPLETE, result.decision());
        assertTrue(result.reasonCodes().contains("MISSING_SOAK_24H"));
    }

    @Test
    void reportIsDeterministicAndContainsOnlyBoundedMetadata() throws Exception {
        CertificationBundle bundle = completeBundle();
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();

        CertificationReportWriter writer = new CertificationReportWriter();
        writer.write(bundle, first);
        writer.write(bundle, second);

        assertEquals(
                first.toString(StandardCharsets.UTF_8), second.toString(StandardCharsets.UTF_8));
        String json = first.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"decision\":\"CERTIFIED\""));
        assertFalse(json.contains("known-api-secret-value"));
        assertFalse(json.contains("known-access-token-value"));
    }

    @Test
    void reportWriterCannotBeGivenAForgedAggregateDecision() throws Exception {
        CertificationBundle incomplete =
                new CertificationBundle(
                        "4422a24", List.of(), List.of(), List.of(), List.of(), List.of(), false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new CertificationReportWriter().write(incomplete, output);

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("\"decision\":\"INCOMPLETE\""));
    }

    private static CertificationBundle completeBundle() {
        List<ScenarioEvidence> scenarios =
                CertificationMatrix.requiredRuns().stream()
                        .map(
                                key ->
                                        new ScenarioEvidence(
                                                key,
                                                EvidenceStatus.PASSED,
                                                1,
                                                2,
                                                VERIFIED,
                                                List.of(HASH),
                                                "OK"))
                        .toList();
        List<AcceptanceEvidence> criteria =
                java.util.Arrays.stream(AcceptanceCriterion.values())
                        .map(
                                criterion ->
                                        new AcceptanceEvidence(
                                                criterion, EvidenceStatus.PASSED, List.of(HASH)))
                        .toList();
        List<SoakEvidence> soaks =
                List.of(
                        new SoakEvidence(
                                CertificationGate.DAY_SECONDS,
                                EvidenceStatus.PASSED,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                List.of(HASH)),
                        new SoakEvidence(
                                CertificationGate.WEEK_SECONDS,
                                EvidenceStatus.PASSED,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                List.of(HASH)));
        List<FixtureFreezeEvidence> fixtures =
                List.of(
                        new FixtureFreezeEvidence(
                                CertificationVenue.BYBIT, EvidenceStatus.PASSED, HASH),
                        new FixtureFreezeEvidence(
                                CertificationVenue.DERIBIT, EvidenceStatus.PASSED, HASH));
        return new CertificationBundle(
                "4422a24", scenarios, criteria, soaks, fixtures, List.of(), true);
    }
}
