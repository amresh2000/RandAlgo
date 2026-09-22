package com.penguinsecure.basis.journal.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("chaos")
final class SnapshotPublishChaosIT {
    @TempDir Path directory;

    @Test
    void interruptedTemporaryWriteCannotReplaceVerifiedSnapshot() throws Exception {
        FileCoreSnapshotStore store = new FileCoreSnapshotStore(directory.toAbsolutePath(), 1024);
        SnapshotDescriptor good = new SnapshotDescriptor(1, 1001, 2, 3, 4, 5, 1, 6, 8, 9);
        store.write(good, new byte[] {1});
        Files.write(
                directory.resolve("basis-snapshot-00000000000000000002.bin.tmp"),
                new byte[] {9, 9});

        SnapshotLoadResult loaded = store.loadLatestCompatible(1, 1001, 2, 3, 4, 5);

        assertEquals(SnapshotLoadStatus.LOADED, loaded.status());
        assertEquals(1, loaded.descriptor().snapshotId());
    }
}
