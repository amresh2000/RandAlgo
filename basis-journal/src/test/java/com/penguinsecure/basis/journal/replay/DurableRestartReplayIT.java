package com.penguinsecure.basis.journal.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.recovery.CoreRecoveryState;
import com.penguinsecure.basis.core.recovery.CoreStateRestorer;
import com.penguinsecure.basis.core.recovery.RecoveryValidationStatus;
import com.penguinsecure.basis.core.risk.PartitionedTokenBucket;
import com.penguinsecure.basis.journal.snapshot.CoreSnapshotCodec;
import com.penguinsecure.basis.journal.snapshot.SnapshotDecodeStatus;
import com.penguinsecure.basis.protocol.sbe.EventType;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderEncoder;
import com.penguinsecure.basis.protocol.sbe.PositionEncoder;
import com.penguinsecure.basis.protocol.sbe.Venue;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Certifies snapshot + tail replay at every durable transition boundary in this stream. */
@Tag("replay")
final class DurableRestartReplayIT {
    @Test
    void everyKillPointConvergesToTheUninterruptedSafetyState() {
        final UnsafeBuffer[] facts = {
            position(1, 10), position(2, 25), position(3, 40), position(4, 65)
        };
        final CoreRecoveryState uninterrupted = emptyState();
        replay(uninterrupted, facts, 0, facts.length);

        for (int killPoint = 0; killPoint <= facts.length; killPoint++) {
            final CoreRecoveryState prefix = emptyState();
            replay(prefix, facts, 0, killPoint);

            final CoreSnapshotCodec codec = new CoreSnapshotCodec();
            final var decoded = codec.decode(codec.encode(prefix, 16_384));
            assertEquals(SnapshotDecodeStatus.DECODED, decoded.status());
            final CoreRecoveryState recovered = decoded.state();

            replay(recovered, facts, killPoint, facts.length);

            assertEquals(RecoveryValidationStatus.VALID, CoreStateRestorer.validate(recovered));
            assertEquals(uninterrupted.nextJournalSequence(), recovered.nextJournalSequence());
            assertEquals(uninterrupted.appliedReplayEvents(), recovered.appliedReplayEvents());
            assertEquals(uninterrupted.ledger().strategyId(0), recovered.ledger().strategyId(0));
            assertEquals(
                    uninterrupted.ledger().configurationGeneration(0),
                    recovered.ledger().configurationGeneration(0));
            assertEquals(
                    uninterrupted.ledger().confirmedGross(0), recovered.ledger().confirmedGross(0));
            assertEquals(uninterrupted.ledger().netExposure(0), recovered.ledger().netExposure(0));
            assertEquals(uninterrupted.ledger().position(0), recovered.ledger().position(0));
        }
    }

    private static void replay(
            final CoreRecoveryState state,
            final UnsafeBuffer[] facts,
            final int from,
            final int to) {
        final ArchiveJournalReplay replay =
                new ArchiveJournalReplay(
                        16,
                        state.nextJournalSequence(),
                        new CoreReplayEventHandler(state, DurableRestartReplayIT::unusedBucket));
        for (int index = from; index < to; index++) {
            assertEquals(
                    ReplayStatus.APPLIED,
                    replay.apply(7, 64L * (index + 1), facts[index], 0, facts[index].capacity()));
        }
    }

    private static PartitionedTokenBucket unusedBucket(
            final int strategySlot, final long configurationGeneration) {
        return new PartitionedTokenBucket(1, 1, 1, 1, 1, 1, 1_000, 1);
    }

    private static CoreRecoveryState emptyState() {
        return new CoreRecoveryState(1, 2, 2, 2, 4, 2);
    }

    private static UnsafeBuffer position(final long sequence, final long quantity) {
        final UnsafeBuffer buffer =
                new UnsafeBuffer(
                        new byte
                                [MessageHeaderEncoder.ENCODED_LENGTH
                                        + PositionEncoder.BLOCK_LENGTH]);
        final PositionEncoder encoder =
                new PositionEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder());
        encoder.eventHeader()
                .eventType(EventType.POSITION)
                .eventSequence(sequence)
                .producerId(1)
                .producerEpoch(1)
                .cellId(1)
                .venue(Venue.BYBIT)
                .accountId(1)
                .instrumentId(1)
                .strategyId(11)
                .configurationGeneration(7)
                .sessionGeneration(1)
                .correlationId(sequence)
                .exchangeEpochNanos(sequence)
                .localReceiveEpochNanos(sequence)
                .localReceiveMonoNanos(sequence)
                .causeEventSequence(sequence - 1)
                .flags(0)
                .reasonCode(0);
        encoder.positionQuantity(quantity)
                .canonicalExposure(quantity)
                .averagePriceTicks(100)
                .realizedPnl(0)
                .unrealizedPnl(0)
                .strategySlot(0)
                .confirmedGross(quantity)
                .pendingGross(0)
                .netExposure(quantity)
                .pendingNetExposure(0)
                .pendingUnhedgedExposure(0)
                .reservedCollateral(0)
                .dailyLoss(0)
                .activeGroups(0)
                .unknownGroups(0);
        return buffer;
    }
}
