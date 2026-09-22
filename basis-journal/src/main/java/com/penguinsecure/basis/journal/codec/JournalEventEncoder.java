package com.penguinsecure.basis.journal.codec;

import com.penguinsecure.basis.journal.ingress.JournalEventClass;
import com.penguinsecure.basis.journal.ingress.JournalIngress;
import com.penguinsecure.basis.journal.ingress.JournalOfferStatus;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderDecoder;
import org.agrona.DirectBuffer;

/** Validates a complete generated-SBE frame before bounded admission. */
public final class JournalEventEncoder {
    private final JournalIngress ingress;
    private final MessageHeaderDecoder header = new MessageHeaderDecoder();

    public JournalEventEncoder(final JournalIngress ingress) {
        if (ingress == null) throw new IllegalArgumentException("ingress required");
        this.ingress = ingress;
    }

    public JournalOfferStatus offer(
            final JournalEventClass eventClass,
            final long producerSequence,
            final DirectBuffer frame,
            final int offset,
            final int length,
            final long nowNanos) {
        if (frame == null
                || offset < 0
                || length < MessageHeaderDecoder.ENCODED_LENGTH
                || offset + length > frame.capacity()) {
            return JournalOfferStatus.INVALID_CLASSIFICATION;
        }
        header.wrap(frame, offset);
        if (header.schemaId() != JournalEventDecoder.SCHEMA_ID
                || header.version() > 2
                || header.blockLength() > length - MessageHeaderDecoder.ENCODED_LENGTH
                || !JournalTemplateClassifications.valid(header.templateId(), eventClass)) {
            return JournalOfferStatus.INVALID_CLASSIFICATION;
        }
        return ingress.offer(
                eventClass, header.templateId(), producerSequence, frame, offset, length, nowNanos);
    }
}
