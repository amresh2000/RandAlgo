package com.penguinsecure.basis.journal.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.protocol.sbe.EventType;
import com.penguinsecure.basis.protocol.sbe.JournalFaultEncoder;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderEncoder;
import com.penguinsecure.basis.protocol.sbe.Venue;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ArchiveJournalReplayTest {
    @Test
    void exactDuplicateIsIdempotentButConflictingDuplicateFailsClosed() {
        ArchiveJournalReplay replay =
                new ArchiveJournalReplay(16, (sequence, template, b, o, l) -> true);
        UnsafeBuffer first = frame(1, 0);

        assertEquals(ReplayStatus.APPLIED, replay.apply(7, 64, first, 0, first.capacity()));
        assertEquals(ReplayStatus.EXACT_DUPLICATE, replay.apply(7, 64, first, 0, first.capacity()));
        assertEquals(
                ReplayStatus.CONFLICTING_DUPLICATE,
                replay.apply(7, 64, frame(1, 99), 0, first.capacity()));
        assertEquals(1, replay.report().applied());
        assertEquals(1, replay.report().exactDuplicates());
    }

    @Test
    void rejectsSequenceGap() {
        ArchiveJournalReplay replay =
                new ArchiveJournalReplay(16, (sequence, template, b, o, l) -> true);
        UnsafeBuffer frame = frame(2, 0);
        assertEquals(ReplayStatus.SEQUENCE_GAP, replay.apply(7, 64, frame, 0, frame.capacity()));
    }

    @Test
    void distinguishesUnknownTemplatesAndIncompatibleSchemas() {
        ArchiveJournalReplay replay =
                new ArchiveJournalReplay(16, (sequence, template, b, o, l) -> true);
        UnsafeBuffer unknown = frame(1, 0);
        unknown.putShort(2, (short) 99, java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(
                ReplayStatus.UNKNOWN_TEMPLATE, replay.apply(7, 64, unknown, 0, unknown.capacity()));

        replay = new ArchiveJournalReplay(16, (sequence, template, b, o, l) -> true);
        UnsafeBuffer wrongSchema = frame(1, 0);
        wrongSchema.putShort(4, (short) 999, java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(
                ReplayStatus.SCHEMA_MISMATCH,
                replay.apply(7, 64, wrongSchema, 0, wrongSchema.capacity()));
    }

    private static UnsafeBuffer frame(final long sequence, final long publicationResult) {
        byte[] bytes =
                new byte[MessageHeaderEncoder.ENCODED_LENGTH + JournalFaultEncoder.BLOCK_LENGTH];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        JournalFaultEncoder encoder =
                new JournalFaultEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder());
        encoder.eventHeader()
                .eventType(EventType.JOURNAL_FAULT)
                .eventSequence(sequence)
                .producerId(1)
                .producerEpoch(1)
                .cellId(1)
                .venue(Venue.NULL_VAL)
                .accountId(0)
                .instrumentId(0)
                .strategyId(0)
                .configurationGeneration(1)
                .sessionGeneration(1)
                .correlationId(1)
                .exchangeEpochNanos(0)
                .localReceiveEpochNanos(1)
                .localReceiveMonoNanos(1)
                .causeEventSequence(0)
                .flags(0)
                .reasonCode(0);
        encoder.stateCode(1)
                .publicationResult(publicationResult)
                .ingressBytes(0)
                .archiveLagBytes(0)
                .diskUsableBytes(1);
        return buffer;
    }
}
