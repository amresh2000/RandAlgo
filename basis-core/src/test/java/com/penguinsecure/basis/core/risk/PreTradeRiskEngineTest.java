package com.penguinsecure.basis.core.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class PreTradeRiskEngineTest {
    @Test
    void reservesAllCapacityAtomicallyAndRetainsUnknownExposure() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        KillHierarchy kills = new KillHierarchy(16);
        RiskReservationTable reservations = new RiskReservationTable(2, ledger);
        PreTradeRiskEngine engine = new PreTradeRiskEngine(kills, ledger, reservations);
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(2, 4, 1, 0, 0, 0, 100, 1);
        MutableRiskDecision decision = new MutableRiskDecision();

        engine.evaluate(
                RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath()),
                RiskFixtures.envelope(),
                decision);

        assertEquals(RiskDecisionStatus.APPROVED, decision.status());
        assertEquals(100, ledger.pendingGross(0));
        assertEquals(2, bucket.reservedHedgeTokens());
        reservations.markSent(decision.reservation().slot(), decision.reservation().generation());
        reservations.markUnknown(
                decision.reservation().slot(), decision.reservation().generation());
        assertEquals(1, ledger.unknownGroups(0));
        assertEquals(100, ledger.pendingGross(0));
    }

    @Test
    void returnsFirstFailureInMandatoryOrder() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        KillHierarchy kills = new KillHierarchy(16);
        kills.kill(KillScope.STRATEGY, 7, 1);
        PreTradeRiskEngine engine =
                new PreTradeRiskEngine(kills, ledger, new RiskReservationTable(1, ledger));
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(1, 2, 1, 0, 0, 0, 100, 1);
        MutableRiskDecision decision = new MutableRiskDecision();
        PreTradeRiskRequest request = RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath());
        request.sessionsHealthy = false;

        engine.evaluate(request, RiskFixtures.envelope(), decision);

        assertEquals(RiskRejectReason.KILLED, decision.reason());
    }

    @Test
    void changedBookEvidenceFailsClosed() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        PreTradeRiskEngine engine =
                new PreTradeRiskEngine(
                        new KillHierarchy(16), ledger, new RiskReservationTable(1, ledger));
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(1, 2, 1, 0, 0, 0, 100, 1);
        MutableRiskDecision decision = new MutableRiskDecision();
        PreTradeRiskRequest request = RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath());
        request.currentFirstBookSequence++;

        engine.evaluate(request, RiskFixtures.envelope(), decision);

        assertEquals(RiskRejectReason.BOOK_EVIDENCE_CHANGED, decision.reason());
    }

    @Test
    void concurrentGroupsReserveAggregateNetCapacity() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        PreTradeRiskEngine engine =
                new PreTradeRiskEngine(
                        new KillHierarchy(16), ledger, new RiskReservationTable(2, ledger));
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(2, 4, 1, 0, 0, 0, 100, 1);
        MutableRiskDecision first = new MutableRiskDecision();
        MutableRiskDecision second = new MutableRiskDecision();
        RiskEnvelope envelope =
                new RiskEnvelope(
                        7, 1, 1, 2_000, 10_000, 100, 10_000, 100, 10_000, 10_000, 10_000, 10, 10);

        engine.evaluate(
                RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath())
                        .exposure(100, 60, 40, 50, 1_000, 100, 2, 100, 200, false, false),
                envelope,
                first);
        engine.evaluate(
                RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath())
                        .exposure(100, 60, 40, 50, 1_000, 100, 2, 100, 200, false, false),
                envelope,
                second);

        assertEquals(RiskDecisionStatus.APPROVED, first.status());
        assertEquals(60, ledger.pendingNetExposure(0));
        assertEquals(RiskRejectReason.NET_LIMIT, second.reason());
    }

    @Test
    void releasingUnsentReservationRestoresNormalAndHedgeRateCapacity() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        RiskReservationTable reservations = new RiskReservationTable(1, ledger);
        PreTradeRiskEngine engine =
                new PreTradeRiskEngine(new KillHierarchy(16), ledger, reservations);
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(1, 2, 1, 0, 0, 0, 100, 1);
        MutableRiskDecision decision = new MutableRiskDecision();
        engine.evaluate(
                RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath()),
                RiskFixtures.envelope(),
                decision);

        reservations.releaseAuthoritatively(
                decision.reservation().slot(), decision.reservation().generation());

        assertEquals(true, bucket.canReserveInitiation(2, 1_001));
    }

    @Test
    void strictlyRiskReducingActionUsesEmergencyCapacityThroughKillAndLimits() {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        ledger.reconcile(0, 200, 200, 200, 0);
        KillHierarchy kills = new KillHierarchy(16);
        kills.kill(KillScope.STRATEGY, 7, 1);
        PreTradeRiskEngine engine =
                new PreTradeRiskEngine(kills, ledger, new RiskReservationTable(1, ledger));
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(1, 2, 1, 0, 0, 0, 100, 1);
        MutableRiskDecision decision = new MutableRiskDecision();
        RiskEnvelope tightEnvelope =
                new RiskEnvelope(7, 1, 1, 2_000, 50, 50, 50, 50, 50, 50, 50, 10, 1);
        PreTradeRiskRequest request =
                RiskFixtures.validRequest(bucket, RiskFixtures.healthyPath())
                        .exposure(100, -100, 100, 100, 0, 100, 0, 200, 100, false, true);

        engine.evaluate(request, tightEnvelope, decision);

        assertEquals(RiskDecisionStatus.APPROVED, decision.status());
        assertEquals(0, bucket.tokens(RatePartition.EMERGENCY, 1_000));
    }
}
