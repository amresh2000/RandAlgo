package com.penguinsecure.basis.journal.archive;

import org.agrona.DirectBuffer;

/** Append-only event publication seam. */
public interface EventJournal extends AutoCloseable {
    long offer(DirectBuffer buffer, int offset, int length);

    long publicationPosition();

    long recordingPosition();

    long recordingId();

    boolean isConnected();

    String pollError();

    @Override
    void close();
}
