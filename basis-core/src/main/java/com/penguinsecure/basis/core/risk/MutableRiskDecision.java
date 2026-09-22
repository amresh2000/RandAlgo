package com.penguinsecure.basis.core.risk;

/** Caller-owned pre-trade outcome. */
public final class MutableRiskDecision {
    private RiskDecisionStatus status = RiskDecisionStatus.REJECTED;
    private RiskRejectReason reason = RiskRejectReason.INVALID_ARGUMENT;
    private final MutableRiskReservationHandle reservation = new MutableRiskReservationHandle();

    public RiskDecisionStatus status() {
        return status;
    }

    public RiskRejectReason reason() {
        return reason;
    }

    public MutableRiskReservationHandle reservation() {
        return reservation;
    }

    void approve(final int slot, final int generation) {
        status = RiskDecisionStatus.APPROVED;
        reason = RiskRejectReason.NONE;
        reservation.set(slot, generation);
    }

    void reject(final RiskRejectReason newReason) {
        status = RiskDecisionStatus.REJECTED;
        reason = newReason;
        reservation.clear();
    }
}
