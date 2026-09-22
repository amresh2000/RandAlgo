package com.penguinsecure.basis.venue.bybit.order;

public enum BybitTradeResponseKind {
    AUTHENTICATED,
    AUTHENTICATION_FAILED,
    COMMAND_ACCEPTED,
    COMMAND_REJECTED,
    RATE_LIMITED,
    PONG,
    UNKNOWN
}
