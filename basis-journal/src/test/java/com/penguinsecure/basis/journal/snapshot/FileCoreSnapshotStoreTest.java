package com.penguinsecure.basis.journal.snapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileCoreSnapshotStoreTest {
    @TempDir Path directory;

    @Test
    void writesAndLoadsVerifiedSnapshot() throws Exception {
        FileCoreSnapshotStore store = new FileCoreSnapshotStore(directory.toAbsolutePath(), 1024);
        SnapshotDescriptor descriptor = descriptor(7);
        byte[] payload = {1, 2, 3, 4};

        store.write(descriptor, payload);
        SnapshotLoadResult loaded = store.loadLatestCompatible(1, 1001, 2, 3, 4, 5);

        assertEquals(SnapshotLoadStatus.LOADED, loaded.status());
        assertEquals(descriptor, loaded.descriptor());
        assertArrayEquals(payload, loaded.payload());
    }

    @Test
    void skipsCorruptNewestAndFallsBackToOlderVerifiedSnapshot() throws Exception {
        FileCoreSnapshotStore store = new FileCoreSnapshotStore(directory.toAbsolutePath(), 1024);
        store.write(descriptor(7), new byte[] {7});
        store.write(descriptor(8), new byte[] {8});
        Path newest = directory.resolve("basis-snapshot-00000000000000000008.bin");
        byte[] corrupt = Files.readAllBytes(newest);
        corrupt[corrupt.length - 1] ^= 1;
        Files.write(newest, corrupt);

        SnapshotLoadResult loaded = store.loadLatestCompatible(1, 1001, 2, 3, 4, 5);

        assertEquals(SnapshotLoadStatus.LOADED, loaded.status());
        assertEquals(7, loaded.descriptor().snapshotId());
    }

    @Test
    void rejectsTruncatedSnapshotWithoutReturningPayload() throws Exception {
        FileCoreSnapshotStore store = new FileCoreSnapshotStore(directory.toAbsolutePath(), 1024);
        store.write(descriptor(7), new byte[] {7});
        Path snapshot = directory.resolve("basis-snapshot-00000000000000000007.bin");
        Files.write(snapshot, new byte[] {1, 2, 3});

        assertEquals(
                SnapshotLoadStatus.CORRUPT,
                store.loadLatestCompatible(1, 1001, 2, 3, 4, 5).status());
    }

    private static SnapshotDescriptor descriptor(final long id) {
        return new SnapshotDescriptor(1, 1001, 2, 3, 4, 5, id, 6, 128, 9);
    }
}
