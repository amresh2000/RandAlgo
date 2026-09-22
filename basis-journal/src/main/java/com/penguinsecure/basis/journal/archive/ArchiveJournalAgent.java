package com.penguinsecure.basis.journal.archive;

import com.penguinsecure.basis.journal.ingress.JournalHealth;
import com.penguinsecure.basis.journal.ingress.JournalIngress;

/** Bounded nonblocking ingress-to-publication agent. */
public final class ArchiveJournalAgent {
    private final JournalIngress ingress;
    private final EventJournal journal;
    private final JournalHealth health;
    private final long lagHighWaterBytes;
    private long lastPublicationResult;

    public ArchiveJournalAgent(
            final JournalIngress ingress,
            final EventJournal journal,
            final JournalHealth health,
            final long lagHighWaterBytes) {
        if (ingress == null || journal == null || health == null || lagHighWaterBytes <= 0) {
            throw new IllegalArgumentException("invalid archive agent");
        }
        this.ingress = ingress;
        this.journal = journal;
        this.health = health;
        this.lagHighWaterBytes = lagHighWaterBytes;
    }

    public int doWork(final int fragmentLimit, final long nowNanos) {
        final int work =
                ingress.controlledDrainLossless(
                        (sequence, template, buffer, offset, length) -> {
                            lastPublicationResult = journal.offer(buffer, offset, length);
                            final PublicationStatus status =
                                    PublicationStatus.fromResult(lastPublicationResult);
                            if (status == PublicationStatus.CLOSED
                                    || status == PublicationStatus.MAX_POSITION_EXCEEDED
                                    || status == PublicationStatus.UNKNOWN_FAILURE)
                                health.globalFault();
                            else if (status != PublicationStatus.PUBLISHED)
                                health.disarmInitiation();
                            return status == PublicationStatus.PUBLISHED;
                        },
                        fragmentLimit);
        final long publicationPosition = Math.max(0, journal.publicationPosition());
        final long recordingPosition = Math.max(0, journal.recordingPosition());
        final long lag = Math.max(0, publicationPosition - recordingPosition);
        health.publication(lastPublicationResult, lag, nowNanos);
        if (lag > lagHighWaterBytes || journal.pollError() != null) health.disarmInitiation();
        return work;
    }
}
