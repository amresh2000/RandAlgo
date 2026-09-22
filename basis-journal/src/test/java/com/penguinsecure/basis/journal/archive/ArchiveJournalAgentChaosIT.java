package com.penguinsecure.basis.journal.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.journal.ingress.JournalEventClass;
import com.penguinsecure.basis.journal.ingress.JournalHealth;
import com.penguinsecure.basis.journal.ingress.JournalHealthState;
import com.penguinsecure.basis.journal.ingress.JournalIngress;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("chaos")
final class ArchiveJournalAgentChaosIT {
    @Test
    void backpressureDisarmsButDoesNotConsumeRecord() {
        JournalHealth health = new JournalHealth();
        JournalIngress ingress = ingressWithOneRecord(health);
        FakeJournal journal = new FakeJournal();
        journal.result = io.aeron.Publication.BACK_PRESSURED;
        ArchiveJournalAgent agent = new ArchiveJournalAgent(ingress, journal, health, 100);

        assertEquals(0, agent.doWork(1, 10));
        assertEquals(JournalHealthState.INITIATION_DISARMED, health.state());
        journal.result = 64;
        assertEquals(1, agent.doWork(1, 11));
    }

    @Test
    void closedPublicationEscalatesGlobalFault() {
        JournalHealth health = new JournalHealth();
        JournalIngress ingress = ingressWithOneRecord(health);
        FakeJournal journal = new FakeJournal();
        journal.result = io.aeron.Publication.CLOSED;

        new ArchiveJournalAgent(ingress, journal, health, 100).doWork(1, 10);

        assertEquals(JournalHealthState.GLOBAL_FAULT, health.state());
    }

    private static JournalIngress ingressWithOneRecord(final JournalHealth health) {
        JournalIngress ingress = new JournalIngress(1024, 256, 1024, 0, health);
        UnsafeBuffer payload = new UnsafeBuffer(new byte[64]);
        ingress.offer(JournalEventClass.CRITICAL, 19, 1, payload, 0, payload.capacity(), 1);
        return ingress;
    }

    private static final class FakeJournal implements EventJournal {
        private long result;

        @Override
        public long offer(final DirectBuffer buffer, final int offset, final int length) {
            return result;
        }

        @Override
        public long publicationPosition() {
            return Math.max(0, result);
        }

        @Override
        public long recordingPosition() {
            return Math.max(0, result);
        }

        @Override
        public long recordingId() {
            return 1;
        }

        @Override
        public boolean isConnected() {
            return result >= 0;
        }

        @Override
        public String pollError() {
            return null;
        }

        @Override
        public void close() {}
    }
}
