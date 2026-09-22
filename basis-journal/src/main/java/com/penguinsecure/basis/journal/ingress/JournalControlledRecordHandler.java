package com.penguinsecure.basis.journal.ingress;

import org.agrona.DirectBuffer;

/** Returns true only when a record has been durably handed to the publication. */
@FunctionalInterface
public interface JournalControlledRecordHandler {
    boolean onRecord(
            long eventSequence, int templateId, DirectBuffer buffer, int offset, int length);
}
