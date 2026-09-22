package com.penguinsecure.basis.journal.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class JournalIngressTest {
    @Test
    void assignsSequencesOnlyAfterAdmissionAndPreservesFifo() {
        JournalHealth health = new JournalHealth();
        JournalIngress ingress = new JournalIngress(1024, 256, 1024, 40, health);
        UnsafeBuffer payload = new UnsafeBuffer(new byte[64]);

        assertEquals(
                JournalOfferStatus.ACCEPTED,
                ingress.offer(JournalEventClass.IMPORTANT, 3, 1, payload, 0, 64, 10));
        assertEquals(
                JournalOfferStatus.STALE_PRODUCER_SEQUENCE,
                ingress.offer(JournalEventClass.IMPORTANT, 3, 1, payload, 0, 64, 11));
        assertEquals(
                JournalOfferStatus.ACCEPTED,
                ingress.offer(JournalEventClass.CRITICAL, 17, 2, payload, 0, 64, 12));

        List<Long> sequences = new ArrayList<>();
        assertEquals(
                2,
                ingress.drainLossless(
                        (sequence, template, buffer, offset, length) -> sequences.add(sequence),
                        10));
        assertEquals(List.of(41L, 42L), sequences);
    }

    @Test
    void normalTrafficStopsBeforeCriticalReserve() {
        JournalHealth health = new JournalHealth();
        JournalIngress ingress = new JournalIngress(1024, 512, 1024, 0, health);
        UnsafeBuffer payload = new UnsafeBuffer(new byte[100]);

        for (int sequence = 1; sequence <= 4; sequence++) {
            assertEquals(
                    JournalOfferStatus.ACCEPTED,
                    ingress.offer(
                            JournalEventClass.IMPORTANT, 3, sequence, payload, 0, 100, sequence));
        }
        assertEquals(
                JournalOfferStatus.NORMAL_LIMIT_REACHED,
                ingress.offer(JournalEventClass.IMPORTANT, 3, 5, payload, 0, 100, 5));
        assertEquals(
                JournalOfferStatus.ACCEPTED,
                ingress.offer(JournalEventClass.CRITICAL, 17, 5, payload, 0, 100, 6));
        assertEquals(JournalHealthState.INITIATION_DISARMED, health.state());
        assertTrue(health.failedImportantOffers() > 0);
    }

    @Test
    void lossyLaneCannotConsumeLosslessCapacity() {
        JournalHealth health = new JournalHealth();
        JournalIngress ingress = new JournalIngress(1024, 256, 1024, 0, health);
        UnsafeBuffer payload = new UnsafeBuffer(new byte[64]);

        assertEquals(
                JournalOfferStatus.ACCEPTED_LOSSY,
                ingress.offer(JournalEventClass.LOSSY, 12, 1, payload, 0, 64, 1));
        assertEquals(0, ingress.occupancyBytes());
        assertEquals(0, ingress.nextEventSequence());
    }
}
