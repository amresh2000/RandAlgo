package com.penguinsecure.basis.journal.projection;

import java.util.List;

/** Bounded cold projector retry state; it has no reference to core or journal ingress. */
public final class EventProjector {
    private final ProjectionStore store;
    private final String projectionName;
    private final int maximumBatchSize;
    private final int maximumRetries;
    private int consecutiveFailures;

    public EventProjector(
            final ProjectionStore store,
            final String projectionName,
            final int maximumBatchSize,
            final int maximumRetries) {
        if (store == null
                || projectionName == null
                || projectionName.isBlank()
                || maximumBatchSize <= 0
                || maximumRetries < 0) {
            throw new IllegalArgumentException("invalid projector configuration");
        }
        this.store = store;
        this.projectionName = projectionName;
        this.maximumBatchSize = maximumBatchSize;
        this.maximumRetries = maximumRetries;
    }

    public ProjectionResult project(final List<ProjectionEvent> events) {
        if (events == null || events.isEmpty() || events.size() > maximumBatchSize) {
            return ProjectionResult.INVALID_EVENT;
        }
        if (consecutiveFailures > maximumRetries) return ProjectionResult.RETRYABLE_FAILURE;
        ProjectionEvent last = events.get(events.size() - 1);
        ProjectionResult result =
                store.appendBatch(
                        projectionName,
                        events,
                        new ProjectionCheckpoint(
                                projectionName, last.recordingId(), last.fragmentPosition()));
        if (result == ProjectionResult.COMMITTED) consecutiveFailures = 0;
        else consecutiveFailures++;
        return result;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }
}
