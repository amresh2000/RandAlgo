package com.penguinsecure.basis.journal.codec;

import com.penguinsecure.basis.protocol.sbe.MessageHeaderDecoder;
import org.agrona.DirectBuffer;

/** Validates the common SBE envelope before replay dispatch. */
public final class JournalEventDecoder {
    public static final int SCHEMA_ID = 1001;
    public static final int MAXIMUM_TEMPLATE_ID = 19;
    private final MessageHeaderDecoder header = new MessageHeaderDecoder();

    public boolean wrap(final DirectBuffer buffer, final int offset, final int length) {
        if (buffer == null
                || offset < 0
                || length < MessageHeaderDecoder.ENCODED_LENGTH
                || offset + length > buffer.capacity()) return false;
        header.wrap(buffer, offset);
        return header.schemaId() == SCHEMA_ID
                && header.version() <= 2
                && header.templateId() > 0
                && header.templateId() <= MAXIMUM_TEMPLATE_ID
                && header.blockLength() <= length - MessageHeaderDecoder.ENCODED_LENGTH;
    }

    public int templateId() {
        return header.templateId();
    }

    public int actingVersion() {
        return header.version();
    }

    public int blockLength() {
        return header.blockLength();
    }
}
