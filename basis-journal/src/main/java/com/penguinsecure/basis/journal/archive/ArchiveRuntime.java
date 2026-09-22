package com.penguinsecure.basis.journal.archive;

/** Lifecycle seam for embedded today and external Archive later. */
public interface ArchiveRuntime extends AutoCloseable {
    EventJournal journal();

    @Override
    void close();
}
