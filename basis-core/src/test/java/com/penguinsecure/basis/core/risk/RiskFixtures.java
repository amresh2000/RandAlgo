package com.penguinsecure.basis.core.risk;

public final class RiskFixtures {
    private RiskFixtures() {}

    public static HedgePathHealth healthyPath() {
        HedgePathHealth health =
                new HedgePathHealth(
                        new HedgePathHealthConfig(
                                50, 100, 500_000, 800_000, 100, 100, 100, 200, 2));
        HedgePathSample sample =
                new HedgePathSample(0, 0, 8, 0, 0, true, true, true, 8, 2, 0, 10, 20, true);
        health.observe(sample);
        health.observe(sample);
        return health;
    }

    public static PreTradeRiskRequest validRequest(
            final PartitionedTokenBucket bucket, final HedgePathHealth health) {
        return new PreTradeRiskRequest()
                .identity(0, 7, 1, 1, 9, 10)
                .routes(1, 2, 3, 4, 5, 6, 1, 1, 1, 1, true)
                .opportunityEvidence(1, 11, 1, 12, 900, 905, 200, 200, 20, 1_100)
                .currentEvidence(1, 11, 1, 12, 900, 905, true, true, 1_000)
                .nativeOrders(100, 100, 100, 101, 100, 101, 1, 1, 10, 10, 10, 10, 1_000, 1_000)
                .exposure(100, 0, 100, 50, 1_000, 100, 2, 100, 200, false, false)
                .safety(bucket, health);
    }

    public static RiskEnvelope envelope() {
        return new RiskEnvelope(
                7, 1, 1, 2_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10, 10);
    }
}
