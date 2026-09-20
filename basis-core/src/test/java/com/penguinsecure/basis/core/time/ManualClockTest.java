package com.penguinsecure.basis.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class ManualClockTest {
    @Test
    void advancesBothInjectedTimeDomainsDeterministically() {
        ManualClock clock = new ManualClock(10L);
        clock.advance(7L);
        assertEquals(17L, clock.nanoTime());
        assertEquals(17L, clock.epochNanos());
    }
}
