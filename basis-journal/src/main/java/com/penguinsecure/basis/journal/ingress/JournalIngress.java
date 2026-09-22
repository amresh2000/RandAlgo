package com.penguinsecure.basis.journal.ingress;

import com.penguinsecure.basis.protocol.sbe.EventHeaderEncoder;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.ControlledMessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RecordDescriptor;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

/** One-producer/one-consumer, bounded journal ingress with a byte-counted critical reserve. */
public final class JournalIngress {
    private static final int ENVELOPE_LENGTH = Long.BYTES + Integer.BYTES + Integer.BYTES;
    private static final int LOSSLESS_MESSAGE_TYPE = 1;
    private static final int LOSSY_MESSAGE_TYPE = 2;
    private static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;

    private final RingBuffer lossless;
    private final RingBuffer lossy;
    private final int normalLimitBytes;
    private final int criticalReserveBytes;
    private final JournalHealth health;
    private long nextEventSequence;
    private long lastProducerSequence;

    public JournalIngress(
            final int losslessCapacityBytes,
            final int criticalReserveBytes,
            final int lossyCapacityBytes,
            final long initialEventSequence,
            final JournalHealth health) {
        if (Integer.bitCount(losslessCapacityBytes) != 1
                || Integer.bitCount(lossyCapacityBytes) != 1
                || criticalReserveBytes <= 0
                || criticalReserveBytes >= losslessCapacityBytes
                || initialEventSequence < 0
                || health == null) {
            throw new IllegalArgumentException("invalid journal ingress configuration");
        }
        lossless = ring(losslessCapacityBytes);
        lossy = ring(lossyCapacityBytes);
        this.criticalReserveBytes = criticalReserveBytes;
        normalLimitBytes = losslessCapacityBytes - criticalReserveBytes;
        nextEventSequence = initialEventSequence;
        this.health = health;
        health.admitted(0, criticalReserveBytes, 0);
    }

    public JournalOfferStatus offer(
            final JournalEventClass eventClass,
            final int templateId,
            final long producerSequence,
            final DirectBuffer source,
            final int sourceOffset,
            final int length,
            final long nowNanos) {
        if (eventClass == null || templateId <= 0 || source == null || length <= 0) {
            return JournalOfferStatus.INVALID_CLASSIFICATION;
        }
        if (producerSequence <= lastProducerSequence) {
            return JournalOfferStatus.STALE_PRODUCER_SEQUENCE;
        }
        final int maximumMessageLength =
                eventClass == JournalEventClass.LOSSY
                        ? lossy.maxMsgLength()
                        : lossless.maxMsgLength() - ENVELOPE_LENGTH;
        if (length > maximumMessageLength) {
            health.failed(eventClass, eventClass == JournalEventClass.CRITICAL);
            return JournalOfferStatus.OVERSIZE;
        }
        if (eventClass == JournalEventClass.LOSSY) {
            final boolean written = lossy.write(LOSSY_MESSAGE_TYPE, source, sourceOffset, length);
            if (!written) health.failed(eventClass, false);
            else lastProducerSequence = producerSequence;
            return written ? JournalOfferStatus.ACCEPTED_LOSSY : JournalOfferStatus.LOSSY_DROPPED;
        }

        final int required =
                BitUtil.align(
                        length + ENVELOPE_LENGTH + RecordDescriptor.HEADER_LENGTH,
                        RecordDescriptor.ALIGNMENT);
        final int occupancy = lossless.size();
        if (eventClass == JournalEventClass.IMPORTANT && occupancy + required > normalLimitBytes) {
            health.failed(eventClass, false);
            return JournalOfferStatus.NORMAL_LIMIT_REACHED;
        }
        final int claim = lossless.tryClaim(LOSSLESS_MESSAGE_TYPE, length + ENVELOPE_LENGTH);
        if (claim < 0) {
            health.failed(eventClass, eventClass == JournalEventClass.CRITICAL);
            return JournalOfferStatus.CAPACITY_EXHAUSTED;
        }
        final long eventSequence = nextEventSequence + 1;
        lossless.buffer().putLong(claim, eventSequence, ORDER);
        lossless.buffer().putInt(claim + Long.BYTES, templateId, ORDER);
        lossless.buffer().putInt(claim + Long.BYTES + Integer.BYTES, length, ORDER);
        lossless.buffer().putBytes(claim + ENVELOPE_LENGTH, source, sourceOffset, length);
        final int sequenceOffset =
                claim
                        + ENVELOPE_LENGTH
                        + MessageHeaderEncoder.ENCODED_LENGTH
                        + EventHeaderEncoder.eventSequenceEncodingOffset();
        if (sequenceOffset + Long.BYTES <= claim + ENVELOPE_LENGTH + length) {
            lossless.buffer().putLong(sequenceOffset, eventSequence, ORDER);
        }
        lossless.commit(claim);
        nextEventSequence = eventSequence;
        lastProducerSequence = producerSequence;
        final int after = occupancy + required;
        health.admitted(after, Math.max(0, lossless.capacity() - after), nowNanos);
        return JournalOfferStatus.ACCEPTED;
    }

    public int drainLossless(final JournalRecordHandler handler, final int limit) {
        return lossless.read(
                (type, buffer, index, length) ->
                        handler.onRecord(
                                buffer.getLong(index, ORDER),
                                buffer.getInt(index + Long.BYTES, ORDER),
                                buffer,
                                index + ENVELOPE_LENGTH,
                                buffer.getInt(index + Long.BYTES + Integer.BYTES, ORDER)),
                limit);
    }

    public int drainLossy(final JournalRecordHandler handler, final int limit) {
        return lossy.read(
                (type, buffer, index, length) -> handler.onRecord(0, 0, buffer, index, length),
                limit);
    }

    public int controlledDrainLossless(
            final JournalControlledRecordHandler handler, final int limit) {
        return lossless.controlledRead(
                (type, buffer, index, length) ->
                        handler.onRecord(
                                        buffer.getLong(index, ORDER),
                                        buffer.getInt(index + Long.BYTES, ORDER),
                                        buffer,
                                        index + ENVELOPE_LENGTH,
                                        buffer.getInt(index + Long.BYTES + Integer.BYTES, ORDER))
                                ? ControlledMessageHandler.Action.COMMIT
                                : ControlledMessageHandler.Action.ABORT,
                limit);
    }

    public int occupancyBytes() {
        return lossless.size();
    }

    public int criticalReserveRemainingBytes() {
        return Math.min(criticalReserveBytes, lossless.capacity() - lossless.size());
    }

    public long nextEventSequence() {
        return nextEventSequence;
    }

    private static RingBuffer ring(final int capacity) {
        return new ManyToOneRingBuffer(
                new UnsafeBuffer(
                        ByteBuffer.allocateDirect(capacity + RingBufferDescriptor.TRAILER_LENGTH)));
    }
}
