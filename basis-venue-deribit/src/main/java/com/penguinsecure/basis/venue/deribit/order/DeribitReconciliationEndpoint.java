package com.penguinsecure.basis.venue.deribit.order;

/** Auditable authenticated/public evidence endpoints required for Deribit certification. */
public enum DeribitReconciliationEndpoint {
    OPEN_ORDERS("private/get_open_orders_by_instrument"),
    ORDER_HISTORY("private/get_order_history_by_currency"),
    USER_TRADES("private/get_user_trades_by_instrument_and_time"),
    POSITIONS("private/get_positions"),
    ACCOUNT_SUMMARY("private/get_account_summary"),
    INSTRUMENT("public/get_instrument"),
    FUNDING_HISTORY("public/get_funding_rate_history"),
    SERVER_TIME("public/get_time"),
    TRANSACTION_LOG("private/get_transaction_log");

    private final String path;

    DeribitReconciliationEndpoint(final String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
