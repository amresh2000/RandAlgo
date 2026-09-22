package com.penguinsecure.basis.venue.bybit.order;

/** Bybit V5 REST HMAC request builder for bounded reconciliation traffic. */
public final class BybitRestRequestSigner {
    private final BybitOrderProfile profile;
    private final StringBuilder payload = new StringBuilder(1_024);
    private final StringBuilder apiKey = new StringBuilder(128);
    private final StringBuilder signature = new StringBuilder(64);

    public BybitRestRequestSigner(final BybitOrderProfile profile) {
        if (profile == null) throw new NullPointerException("profile is required");
        this.profile = profile;
    }

    public void sign(
            final String method,
            final String path,
            final String queryOrBody,
            final long timestampMillis,
            final BybitCredentials credentials,
            final MutableBybitRestRequest destination) {
        if (!("GET".equals(method) || "POST".equals(method))
                || path == null
                || !path.startsWith("/v5/")
                || queryOrBody == null
                || timestampMillis <= 0
                || credentials == null
                || destination == null) {
            throw new IllegalArgumentException("invalid REST signing request");
        }
        apiKey.setLength(0);
        credentials.appendApiKey(apiKey);
        payload.setLength(0);
        payload.append(timestampMillis)
                .append(apiKey)
                .append(profile.receiveWindowMillis())
                .append(queryOrBody);
        signature.setLength(0);
        credentials.signAscii(payload, signature);
        destination.set(
                method,
                path,
                queryOrBody,
                timestampMillis,
                profile.receiveWindowMillis(),
                credentials,
                signature);
        clear(apiKey);
        clear(signature);
        clear(payload);
    }

    public String path(final BybitReconciliationEndpoint endpoint) {
        return switch (endpoint) {
            case OPEN_ORDERS -> "/v5/order/realtime";
            case ORDER_HISTORY -> "/v5/order/history";
            case EXECUTIONS -> "/v5/execution/list";
            case POSITIONS -> "/v5/position/list";
            case WALLET -> "/v5/account/wallet-balance";
            case FEE_RATE -> "/v5/account/fee-rate";
            case FUNDING_HISTORY -> "/v5/market/funding/history";
            case SERVER_TIME -> "/v5/market/time";
        };
    }

    public String orderQuery(
            final long startTimeMillis, final byte[] cursor, final int cursorLength) {
        if (startTimeMillis < 0
                || cursor == null
                || cursorLength < 0
                || cursorLength > cursor.length
                || cursorLength > 256) throw new IllegalArgumentException("invalid order query");
        final StringBuilder query = new StringBuilder(384);
        query.append("category=")
                .append(profile.category())
                .append("&symbol=")
                .append(profile.symbol())
                .append("&limit=50");
        if (startTimeMillis > 0) query.append("&startTime=").append(startTimeMillis);
        if (cursorLength > 0) {
            query.append("&cursor=");
            for (int index = 0; index < cursorLength; index++) {
                final int value = cursor[index] & 0xff;
                if (value > 0x7f || value < 0x20)
                    throw new IllegalArgumentException("cursor must be printable ASCII");
                if (unreserved(value)) query.append((char) value);
                else
                    query.append('%')
                            .append(Character.toUpperCase(Character.forDigit(value >>> 4, 16)))
                            .append(Character.toUpperCase(Character.forDigit(value & 0xf, 16)));
            }
        }
        return query.toString();
    }

    private static boolean unreserved(final int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '_'
                || value == '.'
                || value == '~';
    }

    private static void clear(final StringBuilder value) {
        for (int index = 0; index < value.length(); index++) value.setCharAt(index, '\0');
        value.setLength(0);
    }
}
