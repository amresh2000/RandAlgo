package com.penguinsecure.basis.journal.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.journal.ingress.JournalEventClass;
import com.penguinsecure.basis.protocol.sbe.HealthDecoder;
import com.penguinsecure.basis.protocol.sbe.OrderFactDecoder;
import org.junit.jupiter.api.Test;

final class JournalTemplateClassificationsTest {
    @Test
    void classifiesSafetyFactsAsCritical() {
        assertEquals(
                JournalEventClass.CRITICAL,
                JournalTemplateClassifications.classify(OrderFactDecoder.TEMPLATE_ID));
        assertEquals(
                JournalEventClass.LOSSY,
                JournalTemplateClassifications.classify(HealthDecoder.TEMPLATE_ID));
    }
}
