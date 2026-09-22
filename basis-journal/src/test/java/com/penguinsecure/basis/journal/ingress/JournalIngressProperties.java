package com.penguinsecure.basis.journal.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import org.agrona.concurrent.UnsafeBuffer;

@Tag("property")
final class JournalIngressProperties {
    @Property(tries = 100)
    void successfulAdmissionsHaveContiguousSequences(
            @ForAll @IntRange(min = 1, max = 20) final int eventCount) {
        JournalIngress ingress = new JournalIngress(8192, 2048, 1024, 100, new JournalHealth());
        UnsafeBuffer payload = new UnsafeBuffer(new byte[64]);
        for (int index = 1; index <= eventCount; index++) {
            assertEquals(
                    JournalOfferStatus.ACCEPTED,
                    ingress.offer(
                            JournalEventClass.CRITICAL,
                            19,
                            index,
                            payload,
                            0,
                            payload.capacity(),
                            index));
        }
        AtomicLong expected = new AtomicLong(101);
        assertEquals(
                eventCount,
                ingress.drainLossless(
                        (sequence, template, buffer, offset, length) ->
                                assertEquals(expected.getAndIncrement(), sequence),
                        eventCount));
    }
}
