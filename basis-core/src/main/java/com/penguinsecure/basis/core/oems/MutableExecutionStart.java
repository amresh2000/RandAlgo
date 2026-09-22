package com.penguinsecure.basis.core.oems;

import com.penguinsecure.basis.core.risk.RiskRejectReason;

/** Caller-owned result of starting an aggressive execution. */
public final class MutableExecutionStart {
    private OemsStatus status = OemsStatus.INVALID_STATE;
    private RiskRejectReason riskReason = RiskRejectReason.NONE;
    private final MutableSlotHandle group = new MutableSlotHandle();
    private final MutableSlotHandle initiation = new MutableSlotHandle();

    public OemsStatus status() {
        return status;
    }

    public RiskRejectReason riskReason() {
        return riskReason;
    }

    public MutableSlotHandle group() {
        return group;
    }

    public MutableSlotHandle initiation() {
        return initiation;
    }

    void approved() {
        status = OemsStatus.OK;
        riskReason = RiskRejectReason.NONE;
    }

    void fail(final OemsStatus newStatus, final RiskRejectReason reason) {
        status = newStatus;
        riskReason = reason;
        group.clear();
        initiation.clear();
    }
}
