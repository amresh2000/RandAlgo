package com.penguinsecure.basis.venue.bybit.order;

/** Reusable parsed trade-WebSocket control/command response. */
public final class MutableBybitTradeResponse {
    private BybitTradeResponseKind kind = BybitTradeResponseKind.UNKNOWN;
    private int returnCode;
    private long localOrderIdHigh;
    private long localOrderIdLow;
    private long rateLimit;
    private long rateRemaining;
    private long rateResetMillis;

    void set(
            final BybitTradeResponseKind newKind,
            final int newReturnCode,
            final long idHigh,
            final long idLow,
            final long newRateLimit,
            final long newRateRemaining,
            final long newRateResetMillis) {
        kind = newKind;
        returnCode = newReturnCode;
        localOrderIdHigh = idHigh;
        localOrderIdLow = idLow;
        rateLimit = newRateLimit;
        rateRemaining = newRateRemaining;
        rateResetMillis = newRateResetMillis;
    }

    public BybitTradeResponseKind kind() {
        return kind;
    }

    public int returnCode() {
        return returnCode;
    }

    public long localOrderIdHigh() {
        return localOrderIdHigh;
    }

    public long localOrderIdLow() {
        return localOrderIdLow;
    }

    public long rateLimit() {
        return rateLimit;
    }

    public long rateRemaining() {
        return rateRemaining;
    }

    public long rateResetMillis() {
        return rateResetMillis;
    }
}
