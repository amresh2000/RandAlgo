package com.penguinsecure.basis.journal.archive;

import io.aeron.Publication;

/** Stable mapping of Aeron publication outcomes. */
public enum PublicationStatus {
    PUBLISHED,
    BACK_PRESSURED,
    ADMIN_ACTION,
    NOT_CONNECTED,
    CLOSED,
    MAX_POSITION_EXCEEDED,
    UNKNOWN_FAILURE;

    public static PublicationStatus fromResult(final long result) {
        if (result >= 0) return PUBLISHED;
        if (result == Publication.BACK_PRESSURED) return BACK_PRESSURED;
        if (result == Publication.ADMIN_ACTION) return ADMIN_ACTION;
        if (result == Publication.NOT_CONNECTED) return NOT_CONNECTED;
        if (result == Publication.CLOSED) return CLOSED;
        if (result == Publication.MAX_POSITION_EXCEEDED) return MAX_POSITION_EXCEEDED;
        return UNKNOWN_FAILURE;
    }
}
