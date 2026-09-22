package com.penguinsecure.basis.journal.replay;

import com.penguinsecure.basis.journal.codec.JournalEventDecoder;
import com.penguinsecure.basis.protocol.sbe.EventHeaderDecoder;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderDecoder;
import java.util.Arrays;
import org.agrona.DirectBuffer;

/** Bounded duplicate/conflict detector and SBE replay dispatcher. */
public final class ArchiveJournalReplay implements JournalReplay {
    private final long[] recordingIds;
    private final long[] positions;
    private final int[] hashes;
    private final JournalEventDecoder decoder = new JournalEventDecoder();
    private final MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
    private final EventHeaderDecoder eventHeader = new EventHeaderDecoder();
    private final ReplayEventHandler handler;
    private long applied;
    private long duplicates;
    private long lastSequence;
    private long lastPosition = -1;
    private ReplayStatus status = ReplayStatus.APPLIED;

    public ArchiveJournalReplay(final int identityCapacity, final ReplayEventHandler handler) {
        this(identityCapacity, 0, handler);
    }

    public ArchiveJournalReplay(
            final int identityCapacity,
            final long initialEventSequence,
            final ReplayEventHandler handler) {
        if (identityCapacity <= 0 || Integer.bitCount(identityCapacity) != 1 || handler == null) {
            throw new IllegalArgumentException("power-of-two replay identity capacity required");
        }
        recordingIds = new long[identityCapacity];
        positions = new long[identityCapacity];
        hashes = new int[identityCapacity];
        Arrays.fill(positions, -1);
        if (initialEventSequence < 0)
            throw new IllegalArgumentException("invalid initial sequence");
        lastSequence = initialEventSequence;
        this.handler = handler;
    }

    @Override
    public ReplayStatus apply(
            final long recordingId,
            final long fragmentPosition,
            final DirectBuffer buffer,
            final int offset,
            final int length) {
        if (recordingId < 0
                || fragmentPosition < 0
                || buffer == null
                || offset < 0
                || length < MessageHeaderDecoder.ENCODED_LENGTH
                || offset + length > buffer.capacity()) {
            return terminal(ReplayStatus.INVALID_FRAME);
        }
        messageHeader.wrap(buffer, offset);
        if (messageHeader.schemaId() != JournalEventDecoder.SCHEMA_ID
                || messageHeader.version() > 2) {
            return terminal(ReplayStatus.SCHEMA_MISMATCH);
        }
        if (messageHeader.templateId() <= 0
                || messageHeader.templateId() > JournalEventDecoder.MAXIMUM_TEMPLATE_ID) {
            return terminal(ReplayStatus.UNKNOWN_TEMPLATE);
        }
        if (!decoder.wrap(buffer, offset, length)) return terminal(ReplayStatus.INVALID_FRAME);
        final int hash = hash(buffer, offset, length);
        final int identity = identityIndex(recordingId, fragmentPosition);
        if (identity < 0) return terminal(ReplayStatus.INVALID_FRAME);
        if (positions[identity] == fragmentPosition && recordingIds[identity] == recordingId) {
            if (hashes[identity] != hash) return terminal(ReplayStatus.CONFLICTING_DUPLICATE);
            duplicates++;
            return ReplayStatus.EXACT_DUPLICATE;
        }
        if (positions[identity] >= 0) return terminal(ReplayStatus.INVALID_FRAME);
        eventHeader.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH);
        final long sequence = eventHeader.eventSequence();
        if (sequence != lastSequence + 1) return terminal(ReplayStatus.SEQUENCE_GAP);
        if (!handler.onEvent(sequence, decoder.templateId(), buffer, offset, length)) {
            return terminal(ReplayStatus.INVALID_FRAME);
        }
        recordingIds[identity] = recordingId;
        positions[identity] = fragmentPosition;
        hashes[identity] = hash;
        lastSequence = sequence;
        lastPosition = fragmentPosition;
        applied++;
        return ReplayStatus.APPLIED;
    }

    @Override
    public ReplayReport report() {
        return new ReplayReport(status, applied, duplicates, lastSequence, lastPosition);
    }

    private ReplayStatus terminal(final ReplayStatus next) {
        status = next;
        return next;
    }

    private int identityIndex(final long recordingId, final long position) {
        long value = recordingId * 31 + position;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        int index = (int) value & (positions.length - 1);
        for (int probes = 0; probes < positions.length; probes++) {
            if (positions[index] < 0
                    || (positions[index] == position && recordingIds[index] == recordingId)) {
                return index;
            }
            index = (index + 1) & (positions.length - 1);
        }
        return -1;
    }

    private static int hash(final DirectBuffer buffer, final int offset, final int length) {
        int result = 1;
        for (int index = 0; index < length; index++)
            result = 31 * result + buffer.getByte(offset + index);
        return result;
    }
}
