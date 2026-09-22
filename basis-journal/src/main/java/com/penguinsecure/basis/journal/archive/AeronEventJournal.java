package com.penguinsecure.basis.journal.archive;

import io.aeron.ExclusivePublication;
import io.aeron.archive.client.AeronArchive;
import org.agrona.DirectBuffer;

/** Aeron Archive-backed local journal publication. */
public final class AeronEventJournal implements EventJournal {
    private final AeronArchive archive;
    private final ExclusivePublication publication;
    private final String channel;
    private final int streamId;
    private long recordingId;

    AeronEventJournal(
            final AeronArchive archive,
            final ExclusivePublication publication,
            final String channel,
            final int streamId) {
        this.archive = archive;
        this.publication = publication;
        this.channel = channel;
        this.streamId = streamId;
        recordingId = -1;
    }

    @Override
    public long offer(final DirectBuffer buffer, final int offset, final int length) {
        return publication.offer(buffer, offset, length);
    }

    @Override
    public long publicationPosition() {
        return publication.position();
    }

    @Override
    public long recordingPosition() {
        final long id = recordingId();
        return id < 0 ? AeronArchive.NULL_POSITION : archive.getRecordingPosition(id);
    }

    @Override
    public long recordingId() {
        if (recordingId < 0) {
            recordingId =
                    archive.findLastMatchingRecording(
                            0, channel, streamId, publication.sessionId());
        }
        return recordingId;
    }

    @Override
    public boolean isConnected() {
        return publication.isConnected();
    }

    @Override
    public String pollError() {
        return archive.pollForErrorResponse();
    }

    @Override
    public void close() {
        publication.close();
    }
}
