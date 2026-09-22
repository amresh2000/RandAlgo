package com.penguinsecure.basis.journal.replay;

import org.agrona.DirectBuffer;

/** Validated fragment replay seam. */
public interface JournalReplay {
    ReplayStatus apply(
            long recordingId, long fragmentPosition, DirectBuffer buffer, int offset, int length);

    ReplayReport report();
}
