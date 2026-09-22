package com.penguinsecure.basis.journal.projection;

import java.util.List;

/** Transactional append/checkpoint persistence owned only by the cold projector. */
public interface ProjectionStore {
    ProjectionResult appendBatch(
            String projectionName, List<ProjectionEvent> events, ProjectionCheckpoint checkpoint);

    ProjectionResult rebuild(boolean explicitlyAuthorized);
}
