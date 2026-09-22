package com.penguinsecure.basis.journal.projection;

/** Raw SBE bytes plus searchable durable metadata. */
public record ProjectionEvent(
        long recordingId,
        long fragmentPosition,
        long eventSequence,
        int templateId,
        int schemaVersion,
        int eventType,
        int producerId,
        long correlationId,
        int venueId,
        int accountId,
        long instrumentId,
        long strategyId,
        byte[] payload) {
    public ProjectionEvent {
        if (recordingId < 0
                || fragmentPosition < 0
                || eventSequence <= 0
                || templateId <= 0
                || schemaVersion < 0
                || payload == null
                || payload.length == 0) {
            throw new IllegalArgumentException("invalid projected event");
        }
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
