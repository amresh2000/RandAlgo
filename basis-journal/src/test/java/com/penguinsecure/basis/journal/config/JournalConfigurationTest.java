package com.penguinsecure.basis.journal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class JournalConfigurationTest {
    @Test
    void rejectsCriticalReserveBelowCertifiedTail() {
        assertThrows(IllegalArgumentException.class, () -> configuration(127, 2, 64));
    }

    @Test
    void calculatesNormalLimitAndMinimumReserve() {
        JournalConfiguration configuration = configuration(128, 2, 64);
        assertEquals(896, configuration.normalLimitBytes());
        assertEquals(128, JournalConfiguration.minimumCriticalReserve(2, 64));
    }

    private static JournalConfiguration configuration(
            final int reserve, final int children, final int terminalBytes) {
        return new JournalConfiguration(
                Path.of("/tmp/basis-driver"),
                Path.of("/tmp/basis-archive"),
                Path.of("/tmp/basis-snapshots"),
                "aeron:ipc",
                101,
                102,
                1024,
                1024,
                reserve,
                children,
                terminalBytes,
                1024,
                1024);
    }
}
