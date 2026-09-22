package com.penguinsecure.basis.journal.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.oems.ChildOrderRole;
import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.MutableSlotHandle;
import com.penguinsecure.basis.core.oems.OemsStatus;
import com.penguinsecure.basis.core.recovery.CoreRecoveryState;
import com.penguinsecure.basis.core.risk.MutableRiskReservationHandle;
import com.penguinsecure.basis.core.risk.PartitionedTokenBucket;
import com.penguinsecure.basis.core.risk.RiskReservationState;
import com.penguinsecure.basis.core.risk.RiskReservationStatus;
import org.junit.jupiter.api.Test;

final class CoreSnapshotCodecTest {
    @Test
    void roundTripsLogicalStateAndRestoresNonterminalExposureUnknown() {
        CoreRecoveryState source = populatedState();
        CoreSnapshotCodec codec = new CoreSnapshotCodec();

        byte[] encoded = codec.encode(source, 16_384);
        SnapshotDecodeResult result = codec.decode(encoded);

        assertEquals(SnapshotDecodeStatus.DECODED, result.status());
        assertEquals(19, result.state().nextJournalSequence());
        assertEquals(RiskReservationState.UNKNOWN, result.state().reservations().stateAt(0));
        assertEquals(ChildOrderState.UNKNOWN, result.state().children().stateAt(0));
        assertEquals(4, result.state().reservations().generationAt(1));
        assertEquals(5, result.state().groups().generationAt(1));
        assertEquals(6, result.state().children().generationAt(1));
        assertTrue(result.state().ledger().unknownGroups(0) > 0);
    }

    @Test
    void truncatedPayloadNeverExposesPartialState() {
        CoreSnapshotCodec codec = new CoreSnapshotCodec();
        byte[] encoded = codec.encode(populatedState(), 16_384);
        byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 1);

        SnapshotDecodeResult result = codec.decode(truncated);

        assertEquals(SnapshotDecodeStatus.CORRUPT, result.status());
        assertNull(result.state());
    }

    private static CoreRecoveryState populatedState() {
        CoreRecoveryState state = new CoreRecoveryState(1, 2, 2, 2, 4, 2);
        state.ledger().configure(0, 11, 7);
        PartitionedTokenBucket bucket = new PartitionedTokenBucket(10, 10, 2, 1, 1, 1, 1_000, 1);
        assertTrue(bucket.reserveInitiation(2, 1));
        MutableRiskReservationHandle reservation = new MutableRiskReservationHandle();
        assertEquals(
                RiskReservationStatus.OK,
                state.reservations()
                        .reserve(0, 7, 100, 50, 100, 20, 2, 10_000, bucket, reservation));
        MutableSlotHandle group = new MutableSlotHandle();
        assertEquals(
                OemsStatus.OK,
                state.groups()
                        .create(
                                11,
                                7,
                                reservation.slot(),
                                reservation.generation(),
                                100,
                                100,
                                100,
                                10_000,
                                group));
        MutableSlotHandle child = new MutableSlotHandle();
        assertEquals(
                OemsStatus.OK,
                state.children()
                        .create(
                                1,
                                2,
                                group.slot(),
                                ChildOrderRole.INITIATION,
                                1,
                                1,
                                OrderSide.BUY,
                                100,
                                child));
        state.restoreReplayProgress(19, 4);
        state.reservations().restoreFree(1, 4);
        state.groups().restoreFree(1, 5);
        state.children().restoreFree(1, 6);
        return state;
    }
}
