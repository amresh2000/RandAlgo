package com.penguinsecure.basis.journal.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class EventProjectorTest {
    @Test
    void duplicateProjectionKeepsOneDurableKeyAndAdvancesCheckpointAtomically() {
        InMemoryStore store = new InMemoryStore();
        EventProjector projector = new EventProjector(store, "primary", 8, 2);
        ProjectionEvent event = event(64);

        assertEquals(ProjectionResult.COMMITTED, projector.project(List.of(event)));
        assertEquals(ProjectionResult.COMMITTED, projector.project(List.of(event)));
        assertEquals(1, store.keys.size());
        assertEquals(64, store.checkpoint.fragmentPosition());
    }

    @Test
    void rebuildRequiresExplicitAuthorization() {
        InMemoryStore store = new InMemoryStore();
        assertEquals(ProjectionResult.UNAUTHORIZED_REBUILD, store.rebuild(false));
    }

    private static ProjectionEvent event(final long position) {
        return new ProjectionEvent(1, position, 1, 19, 2, 19, 1, 2, 1, 1, 1, 1, new byte[] {1});
    }

    private static final class InMemoryStore implements ProjectionStore {
        private final Set<String> keys = new HashSet<>();
        private ProjectionCheckpoint checkpoint;

        @Override
        public ProjectionResult appendBatch(
                final String name,
                final List<ProjectionEvent> events,
                final ProjectionCheckpoint next) {
            for (ProjectionEvent event : events) {
                keys.add(event.recordingId() + ":" + event.fragmentPosition());
            }
            checkpoint = next;
            return ProjectionResult.COMMITTED;
        }

        @Override
        public ProjectionResult rebuild(final boolean authorized) {
            if (!authorized) return ProjectionResult.UNAUTHORIZED_REBUILD;
            keys.clear();
            checkpoint = null;
            return ProjectionResult.COMMITTED;
        }
    }
}
