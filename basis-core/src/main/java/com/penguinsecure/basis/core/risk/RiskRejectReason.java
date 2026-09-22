package com.penguinsecure.basis.core.risk;

/** Stable first-failure reason in the architecture's mandatory risk-check order. */
public enum RiskRejectReason {
    NONE(0),
    INVALID_ARGUMENT(1),
    KILLED(2),
    SESSION_UNHEALTHY(3),
    SESSION_GENERATION_MISMATCH(4),
    CONFIGURATION_GENERATION_MISMATCH(5),
    ENVELOPE_EXPIRED(6),
    OPPORTUNITY_EXPIRED(7),
    BOOK_UNTRUSTED(8),
    BOOK_EVIDENCE_CHANGED(9),
    BOOK_STALE(10),
    BOOK_SKEW(11),
    NATIVE_QUANTITY_INVALID(12),
    PRICE_INVALID(13),
    PRICE_BAND_EXCEEDED(14),
    GROUP_LIMIT(15),
    GROSS_LIMIT(16),
    NET_LIMIT(17),
    UNHEDGED_LIMIT(18),
    POSITION_LIMIT(19),
    COLLATERAL_LIMIT(20),
    DAILY_LOSS_LIMIT(21),
    HEDGE_LIQUIDITY(22),
    IMBALANCE_LIMIT(23),
    DUPLICATE_OR_RUNAWAY(24),
    RATE_CAPACITY(25),
    HEDGE_PATH_UNHEALTHY(26),
    RISK_NOT_REDUCING(27),
    RESERVATION_CAPACITY(28),
    NUMERIC_FAILURE(29),
    ENVELOPE_GENERATION_MISMATCH(30);

    private final int code;

    RiskRejectReason(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
