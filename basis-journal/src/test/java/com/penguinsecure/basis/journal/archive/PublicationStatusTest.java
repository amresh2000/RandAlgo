package com.penguinsecure.basis.journal.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aeron.Publication;
import org.junit.jupiter.api.Test;

final class PublicationStatusTest {
    @Test
    void mapsEveryDocumentedAeronResult() {
        assertEquals(PublicationStatus.PUBLISHED, PublicationStatus.fromResult(0));
        assertEquals(
                PublicationStatus.BACK_PRESSURED,
                PublicationStatus.fromResult(Publication.BACK_PRESSURED));
        assertEquals(
                PublicationStatus.ADMIN_ACTION,
                PublicationStatus.fromResult(Publication.ADMIN_ACTION));
        assertEquals(
                PublicationStatus.NOT_CONNECTED,
                PublicationStatus.fromResult(Publication.NOT_CONNECTED));
        assertEquals(PublicationStatus.CLOSED, PublicationStatus.fromResult(Publication.CLOSED));
        assertEquals(
                PublicationStatus.MAX_POSITION_EXCEEDED,
                PublicationStatus.fromResult(Publication.MAX_POSITION_EXCEEDED));
    }
}
