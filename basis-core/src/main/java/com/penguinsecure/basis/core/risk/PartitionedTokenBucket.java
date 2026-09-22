package com.penguinsecure.basis.core.risk;

/** Three fixed token buckets with venue-feedback fencing and reserved hedge claims. */
public final class PartitionedTokenBucket {
    private final long[] capacities = new long[RatePartition.values().length];
    private final long[] tokens = new long[RatePartition.values().length];
    private final long[] refillTokens = new long[RatePartition.values().length];
    private final long refillIntervalNanos;
    private long lastRefillNanos;
    private long reservedHedgeTokens;
    private boolean known = true;

    public PartitionedTokenBucket(
            final long normalCapacity,
            final long hedgeCapacity,
            final long emergencyCapacity,
            final long normalRefill,
            final long hedgeRefill,
            final long emergencyRefill,
            final long refillIntervalNanos,
            final long initialMonoNanos) {
        if (normalCapacity <= 0
                || hedgeCapacity <= 0
                || emergencyCapacity <= 0
                || normalRefill < 0
                || hedgeRefill < 0
                || emergencyRefill < 0
                || refillIntervalNanos <= 0
                || initialMonoNanos <= 0) {
            throw new IllegalArgumentException("invalid token-bucket configuration");
        }
        capacities[RatePartition.NORMAL.ordinal()] = normalCapacity;
        capacities[RatePartition.HEDGE.ordinal()] = hedgeCapacity;
        capacities[RatePartition.EMERGENCY.ordinal()] = emergencyCapacity;
        tokens[RatePartition.NORMAL.ordinal()] = normalCapacity;
        tokens[RatePartition.HEDGE.ordinal()] = hedgeCapacity;
        tokens[RatePartition.EMERGENCY.ordinal()] = emergencyCapacity;
        refillTokens[RatePartition.NORMAL.ordinal()] = normalRefill;
        refillTokens[RatePartition.HEDGE.ordinal()] = hedgeRefill;
        refillTokens[RatePartition.EMERGENCY.ordinal()] = emergencyRefill;
        this.refillIntervalNanos = refillIntervalNanos;
        lastRefillNanos = initialMonoNanos;
    }

    public boolean known() {
        return known;
    }

    public void markUnknown() {
        known = false;
    }

    public void applyVenueRemaining(
            final long normalRemaining,
            final long hedgeRemaining,
            final long emergencyRemaining,
            final long nowNanos) {
        if (normalRemaining < 0 || hedgeRemaining < 0 || emergencyRemaining < 0) {
            markUnknown();
            return;
        }
        refill(nowNanos);
        tokens[RatePartition.NORMAL.ordinal()] =
                Math.min(tokens[RatePartition.NORMAL.ordinal()], normalRemaining);
        tokens[RatePartition.HEDGE.ordinal()] =
                Math.min(tokens[RatePartition.HEDGE.ordinal()], hedgeRemaining);
        tokens[RatePartition.EMERGENCY.ordinal()] =
                Math.min(tokens[RatePartition.EMERGENCY.ordinal()], emergencyRemaining);
        known = true;
    }

    public boolean canReserveInitiation(final long hedgeClaims, final long nowNanos) {
        if (hedgeClaims <= 0) return false;
        refill(nowNanos);
        return known
                && tokens[RatePartition.NORMAL.ordinal()] >= 1
                && availableHedgeTokens() >= hedgeClaims;
    }

    public boolean reserveInitiation(final long hedgeClaims, final long nowNanos) {
        if (!canReserveInitiation(hedgeClaims, nowNanos)) return false;
        tokens[RatePartition.NORMAL.ordinal()]--;
        reservedHedgeTokens += hedgeClaims;
        return true;
    }

    public boolean consumeReservedHedge() {
        if (reservedHedgeTokens <= 0 || tokens[RatePartition.HEDGE.ordinal()] <= 0) return false;
        reservedHedgeTokens--;
        tokens[RatePartition.HEDGE.ordinal()]--;
        return true;
    }

    void refundConsumedHedge() {
        final int hedge = RatePartition.HEDGE.ordinal();
        tokens[hedge] = Math.min(capacities[hedge], tokens[hedge] + 1);
        reservedHedgeTokens++;
    }

    public void releaseReservedHedge(final long claims) {
        if (claims < 0 || claims > reservedHedgeTokens) {
            throw new IllegalArgumentException("invalid reserved hedge release");
        }
        reservedHedgeTokens -= claims;
    }

    public void cancelInitiationReservation(final long hedgeClaims) {
        releaseReservedHedge(hedgeClaims);
        final int normal = RatePartition.NORMAL.ordinal();
        tokens[normal] = Math.min(capacities[normal], tokens[normal] + 1);
    }

    public boolean tryConsumeEmergency(final long nowNanos) {
        refill(nowNanos);
        if (!known || tokens[RatePartition.EMERGENCY.ordinal()] <= 0) return false;
        tokens[RatePartition.EMERGENCY.ordinal()]--;
        return true;
    }

    public void refundEmergency() {
        final int emergency = RatePartition.EMERGENCY.ordinal();
        tokens[emergency] = Math.min(capacities[emergency], tokens[emergency] + 1);
    }

    public long tokens(final RatePartition partition, final long nowNanos) {
        if (partition == null) return 0;
        refill(nowNanos);
        return tokens[partition.ordinal()];
    }

    public long reservedHedgeTokens() {
        return reservedHedgeTokens;
    }

    public long capacity(final RatePartition partition) {
        return partition == null ? 0 : capacities[partition.ordinal()];
    }

    public long tokensWithoutRefill(final RatePartition partition) {
        return partition == null ? 0 : tokens[partition.ordinal()];
    }

    public long refillTokens(final RatePartition partition) {
        return partition == null ? 0 : refillTokens[partition.ordinal()];
    }

    public long refillIntervalNanos() {
        return refillIntervalNanos;
    }

    /** Restores claims only when they fit the freshly configured bucket. */
    public void restoreConservatively(
            final long normalTokens,
            final long hedgeTokens,
            final long emergencyTokens,
            final long reservedHedgeTokens) {
        if (normalTokens < 0
                || normalTokens > capacities[RatePartition.NORMAL.ordinal()]
                || hedgeTokens < 0
                || hedgeTokens > capacities[RatePartition.HEDGE.ordinal()]
                || emergencyTokens < 0
                || emergencyTokens > capacities[RatePartition.EMERGENCY.ordinal()]
                || reservedHedgeTokens < 0
                || reservedHedgeTokens > hedgeTokens) {
            throw new IllegalArgumentException("restored token state does not match configuration");
        }
        tokens[RatePartition.NORMAL.ordinal()] = normalTokens;
        tokens[RatePartition.HEDGE.ordinal()] = hedgeTokens;
        tokens[RatePartition.EMERGENCY.ordinal()] = emergencyTokens;
        this.reservedHedgeTokens = reservedHedgeTokens;
        known = false;
    }

    private long availableHedgeTokens() {
        return tokens[RatePartition.HEDGE.ordinal()] - reservedHedgeTokens;
    }

    private void refill(final long nowNanos) {
        if (nowNanos <= lastRefillNanos) return;
        final long intervals = (nowNanos - lastRefillNanos) / refillIntervalNanos;
        if (intervals <= 0) return;
        for (int index = 0; index < tokens.length; index++) {
            final long refill = saturatedMultiply(intervals, refillTokens[index]);
            tokens[index] = Math.min(capacities[index], saturatedAdd(tokens[index], refill));
        }
        final long advance = saturatedMultiply(intervals, refillIntervalNanos);
        lastRefillNanos = saturatedAdd(lastRefillNanos, advance);
    }

    private static long saturatedMultiply(final long left, final long right) {
        if (left == 0 || right == 0) return 0;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatedAdd(final long left, final long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
