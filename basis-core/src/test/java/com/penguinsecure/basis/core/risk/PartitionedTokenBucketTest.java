package com.penguinsecure.basis.core.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class PartitionedTokenBucketTest {
    @Test
    void normalTrafficCannotBorrowReservedHedgeOrEmergencyCapacity() {
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(1, 2, 1, 0, 0, 0, 100, 1);
        assertTrue(bucket.reserveInitiation(2, 2));
        assertFalse(bucket.reserveInitiation(1, 2));
        assertEquals(2, bucket.reservedHedgeTokens());
        assertTrue(bucket.consumeReservedHedge());
        assertTrue(bucket.tryConsumeEmergency(2));
        assertFalse(bucket.tryConsumeEmergency(2));
    }

    @Test
    void unknownVenueFeedbackFailsClosed() {
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(2, 2, 1, 1, 1, 1, 100, 1);
        bucket.markUnknown();
        assertFalse(bucket.canReserveInitiation(1, 2));
        bucket.applyVenueRemaining(2, 2, 1, 2);
        assertTrue(bucket.canReserveInitiation(1, 2));
    }
}
