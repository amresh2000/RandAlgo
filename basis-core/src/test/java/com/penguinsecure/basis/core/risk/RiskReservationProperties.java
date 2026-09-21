package com.penguinsecure.basis.core.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.LongRange;

@Tag("property")
final class RiskReservationProperties {
    @Property(tries = 500)
    void authoritativeReleaseRestoresPendingCapacity(
            @ForAll @LongRange(min = 1, max = 1_000_000) final long gross,
            @ForAll @LongRange(min = 0, max = 1_000_000) final long collateral,
            @ForAll @LongRange(min = 1, max = 8) final long hedgeClaims) {
        StrategyRiskLedger ledger = new StrategyRiskLedger(1);
        ledger.configure(0, 7, 1);
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(1, 8, 1, 0, 0, 0, 100, 1);
        bucket.reserveInitiation(hedgeClaims, 2);
        RiskReservationTable reservations = new RiskReservationTable(1, ledger);
        MutableRiskReservationHandle handle = new MutableRiskReservationHandle();

        assertEquals(
                RiskReservationStatus.OK,
                reservations.reserve(
                        0, 1, gross, 0, gross, collateral, hedgeClaims, 100, bucket, handle));
        assertEquals(
                RiskReservationStatus.OK,
                reservations.releaseAuthoritatively(handle.slot(), handle.generation()));

        assertEquals(0, ledger.pendingGross(0));
        assertEquals(0, ledger.reservedCollateral(0));
        assertEquals(0, ledger.pendingNetExposure(0));
        assertEquals(0, ledger.pendingUnhedgedExposure(0));
        assertEquals(0, ledger.activeGroups(0));
        assertEquals(0, bucket.reservedHedgeTokens());
    }
}
