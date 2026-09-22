package com.penguinsecure.basis.journal.replay;

import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;

/** Cold bounded polling session for one exact Archive recording range. */
public final class AeronReplaySession implements AutoCloseable {
    private final Subscription subscription;
    private final FragmentAssembler assembler;

    public AeronReplaySession(
            final AeronArchive archive,
            final long recordingId,
            final long startPosition,
            final long length,
            final String replayChannel,
            final int replayStreamId,
            final JournalReplay replay) {
        if (archive == null
                || replay == null
                || recordingId < 0
                || startPosition < 0
                || length < 0
                || replayChannel == null
                || replayChannel.isBlank()
                || replayStreamId <= 0) {
            throw new IllegalArgumentException("invalid replay session");
        }
        final long retainedStart = archive.getStartPosition(recordingId);
        final long retainedStop = archive.getStopPosition(recordingId);
        if (retainedStart < 0
                || startPosition < retainedStart
                || (retainedStop >= 0 && startPosition > retainedStop)) {
            throw new IllegalArgumentException(
                    "snapshot position outside retained recording range");
        }
        subscription =
                archive.replay(recordingId, startPosition, length, replayChannel, replayStreamId);
        assembler =
                new FragmentAssembler(
                        (buffer, offset, fragmentLength, header) ->
                                replay.apply(
                                        recordingId,
                                        header.position(),
                                        buffer,
                                        offset,
                                        fragmentLength));
    }

    public int doWork(final int fragmentLimit) {
        return subscription.poll(assembler, fragmentLimit);
    }

    public boolean isConnected() {
        return subscription.isConnected();
    }

    @Override
    public void close() {
        subscription.close();
    }
}
