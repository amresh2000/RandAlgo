package com.penguinsecure.basis.journal.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.journal.ingress.JournalEventClass;
import com.penguinsecure.basis.journal.ingress.JournalHealth;
import com.penguinsecure.basis.journal.ingress.JournalIngress;
import com.penguinsecure.basis.journal.ingress.JournalOfferStatus;
import com.penguinsecure.basis.protocol.sbe.EventHeaderDecoder;
import com.penguinsecure.basis.protocol.sbe.EventType;
import com.penguinsecure.basis.protocol.sbe.JournalFaultEncoder;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderDecoder;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderEncoder;
import com.penguinsecure.basis.protocol.sbe.Venue;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class JournalEventEncoderTest {
    @Test
    void writesSuccessfulAdmissionSequenceIntoDurableSbeFrame() {
        JournalIngress ingress = new JournalIngress(4096, 512, 1024, 10, new JournalHealth());
        JournalEventEncoder facade = new JournalEventEncoder(ingress);
        UnsafeBuffer frame = faultFrame();

        assertEquals(
                JournalOfferStatus.ACCEPTED,
                facade.offer(JournalEventClass.CRITICAL, 1, frame, 0, frame.capacity(), 1));
        AtomicLong durableSequence = new AtomicLong();
        ingress.drainLossless(
                (sequence, template, buffer, offset, length) -> {
                    EventHeaderDecoder event =
                            new EventHeaderDecoder()
                                    .wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH);
                    durableSequence.set(event.eventSequence());
                },
                1);
        assertEquals(11, durableSequence.get());
    }

    @Test
    void rejectsCriticalTemplateOfferedAsLossy() {
        JournalIngress ingress = new JournalIngress(4096, 512, 1024, 0, new JournalHealth());
        UnsafeBuffer frame = faultFrame();
        assertEquals(
                JournalOfferStatus.INVALID_CLASSIFICATION,
                new JournalEventEncoder(ingress)
                        .offer(JournalEventClass.LOSSY, 1, frame, 0, frame.capacity(), 1));
    }

    private static UnsafeBuffer faultFrame() {
        byte[] bytes =
                new byte[MessageHeaderEncoder.ENCODED_LENGTH + JournalFaultEncoder.BLOCK_LENGTH];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        JournalFaultEncoder encoder =
                new JournalFaultEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder());
        encoder.eventHeader()
                .eventType(EventType.JOURNAL_FAULT)
                .eventSequence(0)
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
                .publicationResult(0)
                .ingressBytes(0)
                .archiveLagBytes(0)
                .diskUsableBytes(1);
        return buffer;
    }
}
