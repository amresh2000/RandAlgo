package com.penguinsecure.basis.app.certification;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Phase 12 exit gate. Every missing or duplicate item fails closed. */
public final class CertificationGate {
    public static final long DAY_SECONDS = 86_400;
    public static final long WEEK_SECONDS = 7 * DAY_SECONDS;

    public CertificationGateResult evaluate(final CertificationBundle bundle) {
        if (bundle == null) throw new NullPointerException("bundle is required");
        final List<String> reasons = new ArrayList<>();
        boolean observedFailure = false;

        final Map<ScenarioKey, ScenarioEvidence> scenarios = new HashMap<>();
        for (ScenarioEvidence evidence : bundle.scenarios()) {
            if (scenarios.putIfAbsent(evidence.key(), evidence) != null) {
                reasons.add("DUPLICATE_SCENARIO_" + key(evidence.key()));
                observedFailure = true;
            }
        }
        for (ScenarioKey required : CertificationMatrix.requiredRuns()) {
            final ScenarioEvidence evidence = scenarios.get(required);
            if (evidence == null || evidence.status() == EvidenceStatus.NOT_RUN) {
                reasons.add("MISSING_SCENARIO_" + key(required));
            } else if (evidence.status() != EvidenceStatus.PASSED) {
                reasons.add("SCENARIO_NOT_PASSED_" + key(required));
                observedFailure |= evidence.status() == EvidenceStatus.FAILED;
            }
        }

        final Map<AcceptanceCriterion, AcceptanceEvidence> criteria =
                new EnumMap<>(AcceptanceCriterion.class);
        for (AcceptanceEvidence evidence : bundle.acceptanceCriteria()) {
            if (criteria.putIfAbsent(evidence.criterion(), evidence) != null) {
                reasons.add("DUPLICATE_CRITERION_" + evidence.criterion());
                observedFailure = true;
            }
        }
        for (AcceptanceCriterion criterion : AcceptanceCriterion.values()) {
            final AcceptanceEvidence evidence = criteria.get(criterion);
            if (evidence == null || evidence.status() == EvidenceStatus.NOT_RUN) {
                reasons.add("MISSING_CRITERION_" + criterion);
            } else if (evidence.status() != EvidenceStatus.PASSED) {
                reasons.add("CRITERION_NOT_PASSED_" + criterion);
                observedFailure |= evidence.status() == EvidenceStatus.FAILED;
            }
        }

        observedFailure |= evaluateSoaks(bundle.soaks(), reasons);
        observedFailure |= evaluateFixtures(bundle.fixtures(), reasons);
        for (CertificationFinding finding : bundle.findings()) {
            if (!finding.resolved() && finding.severity() <= 2) {
                reasons.add("UNRESOLVED_SEV" + finding.severity() + "_" + finding.id());
                observedFailure = true;
            }
        }
        if (!bundle.testnetLimitationsDocumented()) reasons.add("MISSING_TESTNET_LIMITATIONS");

        if (reasons.isEmpty()) {
            return new CertificationGateResult(CertificationDecision.CERTIFIED, List.of());
        }
        return new CertificationGateResult(
                observedFailure ? CertificationDecision.FAILED : CertificationDecision.INCOMPLETE,
                reasons);
    }

    private static boolean evaluateSoaks(
            final List<SoakEvidence> evidence, final List<String> reasons) {
        boolean failure = false;
        failure |= evaluateSoak(DAY_SECONDS, WEEK_SECONDS, evidence, reasons);
        failure |= evaluateSoak(WEEK_SECONDS, Long.MAX_VALUE, evidence, reasons);
        return failure;
    }

    private static boolean evaluateSoak(
            final long minimumDuration,
            final long exclusiveMaximumDuration,
            final List<SoakEvidence> evidence,
            final List<String> reasons) {
        SoakEvidence candidate = null;
        int candidates = 0;
        for (SoakEvidence item : evidence) {
            if (item.durationSeconds() >= minimumDuration
                    && item.durationSeconds() < exclusiveMaximumDuration) {
                candidates++;
                if (candidate == null || item.durationSeconds() < candidate.durationSeconds()) {
                    candidate = item;
                }
            }
        }
        final String label = minimumDuration == DAY_SECONDS ? "24H" : "7D";
        if (candidates > 1) {
            reasons.add("DUPLICATE_SOAK_" + label);
            return true;
        }
        if (candidate == null || candidate.status() == EvidenceStatus.NOT_RUN) {
            reasons.add("MISSING_SOAK_" + label);
            return false;
        }
        if (candidate.status() != EvidenceStatus.PASSED) {
            reasons.add("SOAK_NOT_PASSED_" + label);
            return candidate.status() == EvidenceStatus.FAILED;
        }
        return false;
    }

    private static boolean evaluateFixtures(
            final List<FixtureFreezeEvidence> evidence, final List<String> reasons) {
        final Map<CertificationVenue, FixtureFreezeEvidence> fixtures =
                new EnumMap<>(CertificationVenue.class);
        boolean failure = false;
        for (FixtureFreezeEvidence item : evidence) {
            if (fixtures.putIfAbsent(item.venue(), item) != null) {
                reasons.add("DUPLICATE_FIXTURE_" + item.venue());
                failure = true;
            }
        }
        for (CertificationVenue venue :
                List.of(CertificationVenue.BYBIT, CertificationVenue.DERIBIT)) {
            final FixtureFreezeEvidence item = fixtures.get(venue);
            if (item == null || item.status() == EvidenceStatus.NOT_RUN) {
                reasons.add("MISSING_FIXTURE_" + venue);
            } else if (item.status() != EvidenceStatus.PASSED) {
                reasons.add("FIXTURE_NOT_PASSED_" + venue);
                failure |= item.status() == EvidenceStatus.FAILED;
            }
        }
        return failure;
    }

    private static String key(final ScenarioKey key) {
        return key.scenario() + "_" + key.venue();
    }
}
