package com.penguinsecure.basis.journal.replay;

import com.penguinsecure.basis.core.risk.PartitionedTokenBucket;

/** Rebinds replayed reservations to the current immutable rate configuration. */
@FunctionalInterface
public interface RecoveryRateBucketFactory {
    PartitionedTokenBucket create(int strategySlot, long configurationGeneration);
}
