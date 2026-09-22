package com.penguinsecure.basis.journal.archive;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.journal.config.JournalConfiguration;
import java.nio.file.Path;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("replay")
final class AeronArchiveRuntimeIT {
    @TempDir Path directory;

    @Test
    void recordsPublicationToEmbeddedArchive() throws Exception {
        JournalConfiguration configuration =
                new JournalConfiguration(
                        directory.resolve("driver").toAbsolutePath(),
                        directory.resolve("archive").toAbsolutePath(),
                        directory.resolve("snapshots").toAbsolutePath(),
                        "aeron:ipc",
                        1101,
                        1102,
                        4096,
                        1024,
                        512,
                        2,
                        128,
                        1_048_576,
                        1_048_576);
        try (AeronArchiveRuntime runtime = AeronArchiveRuntime.launch(configuration, true)) {
            EventJournal journal = runtime.journal();
            UnsafeBuffer payload = new UnsafeBuffer(new byte[64]);
            long publicationPosition = awaitOffer(journal, payload);
            assertTrue(publicationPosition > 0);
            assertTrue(awaitRecording(journal, publicationPosition));
            assertTrue(journal.recordingId() >= 0);
        }
    }

    private static long awaitOffer(final EventJournal journal, final UnsafeBuffer payload) {
        final long deadline = System.nanoTime() + 10_000_000_000L;
        long result;
        do {
            result = journal.offer(payload, 0, payload.capacity());
            if (result >= 0) return result;
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return result;
    }

    private static boolean awaitRecording(
            final EventJournal journal, final long publicationPosition) {
        final long deadline = System.nanoTime() + 10_000_000_000L;
        do {
            if (journal.recordingPosition() >= publicationPosition) return true;
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return false;
    }
}
