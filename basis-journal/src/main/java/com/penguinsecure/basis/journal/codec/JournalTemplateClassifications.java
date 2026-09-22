package com.penguinsecure.basis.journal.codec;

import com.penguinsecure.basis.journal.ingress.JournalEventClass;
import com.penguinsecure.basis.protocol.sbe.ExecutionGroupDecoder;
import com.penguinsecure.basis.protocol.sbe.FillDecoder;
import com.penguinsecure.basis.protocol.sbe.HealthDecoder;
import com.penguinsecure.basis.protocol.sbe.JournalFaultDecoder;
import com.penguinsecure.basis.protocol.sbe.KillStateDecoder;
import com.penguinsecure.basis.protocol.sbe.OperatorControlDecoder;
import com.penguinsecure.basis.protocol.sbe.OrderFactDecoder;
import com.penguinsecure.basis.protocol.sbe.OrderStateDecoder;
import com.penguinsecure.basis.protocol.sbe.PositionDecoder;
import com.penguinsecure.basis.protocol.sbe.ReconciliationResultDecoder;
import com.penguinsecure.basis.protocol.sbe.RiskDecisionDecoder;
import com.penguinsecure.basis.protocol.sbe.RiskReservationStateDecoder;
import com.penguinsecure.basis.protocol.sbe.SnapshotMarkerDecoder;

/** Stable template-to-admission policy; event classification is not encoded in the wire payload. */
public final class JournalTemplateClassifications {
    private JournalTemplateClassifications() {}

    public static JournalEventClass classify(final int templateId) {
        if (templateId == RiskDecisionDecoder.TEMPLATE_ID
                || templateId == ExecutionGroupDecoder.TEMPLATE_ID
                || templateId == OrderStateDecoder.TEMPLATE_ID
                || templateId == FillDecoder.TEMPLATE_ID
                || templateId == PositionDecoder.TEMPLATE_ID
                || templateId == OperatorControlDecoder.TEMPLATE_ID
                || templateId == SnapshotMarkerDecoder.TEMPLATE_ID
                || templateId == OrderFactDecoder.TEMPLATE_ID
                || templateId == RiskReservationStateDecoder.TEMPLATE_ID
                || templateId == KillStateDecoder.TEMPLATE_ID
                || templateId == JournalFaultDecoder.TEMPLATE_ID
                || templateId == ReconciliationResultDecoder.TEMPLATE_ID) {
            return JournalEventClass.CRITICAL;
        }
        if (templateId == HealthDecoder.TEMPLATE_ID) return JournalEventClass.LOSSY;
        return JournalEventClass.IMPORTANT;
    }

    public static boolean valid(final int templateId, final JournalEventClass eventClass) {
        return eventClass != null && classify(templateId) == eventClass;
    }
}
