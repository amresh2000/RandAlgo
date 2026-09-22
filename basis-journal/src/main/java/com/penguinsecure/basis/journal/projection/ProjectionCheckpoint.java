package com.penguinsecure.basis.journal.projection;

/** Last committed archive identity for one rebuildable projection. */
public record ProjectionCheckpoint(String projectionName, long recordingId, long fragmentPosition) {
    public ProjectionCheckpoint {
        if (projectionName == null
                || projectionName.isBlank()
                || recordingId < 0
                || fragmentPosition < 0) {
            throw new IllegalArgumentException("invalid projection checkpoint");
        }
    }
}
