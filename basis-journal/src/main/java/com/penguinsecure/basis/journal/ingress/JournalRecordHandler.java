package com.penguinsecure.basis.journal.ingress;

import org.agrona.DirectBuffer;

/** Allocation-free callback for an admitted encoded event. */
@FunctionalInterface
public interface JournalRecordHandler {
    void onRecord(long eventSequence, int templateId, DirectBuffer buffer, int offset, int length);
}
