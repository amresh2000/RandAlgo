package com.penguinsecure.basis.journal.snapshot;

import java.io.IOException;

/** Atomic persistence and compatible snapshot discovery. */
public interface CoreSnapshotStore {
    void write(SnapshotDescriptor descriptor, byte[] payload) throws IOException;

    SnapshotLoadResult loadLatestCompatible(
            int formatVersion,
            int schemaId,
            int maximumSchemaVersion,
            long buildGeneration,
            long configurationGeneration,
            long catalogGeneration);
}
