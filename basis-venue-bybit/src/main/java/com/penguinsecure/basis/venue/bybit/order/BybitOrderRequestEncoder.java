package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;

/** Reusable deterministic encoder for Bybit V5 trade WebSocket requests. */
public final class BybitOrderRequestEncoder {
    private final BybitOrderProfile profile;
    private final byte[] clientId = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
    private final MutableLocalOrderId localId = new MutableLocalOrderId();
    private final StringBuilder output = new StringBuilder(512);
    private final StringBuilder signatureInput = new StringBuilder(256);
    private final StringBuilder signature = new StringBuilder(64);

    public BybitOrderRequestEncoder(final BybitOrderProfile profile) {
        if (profile == null) throw new NullPointerException("profile is required");
        this.profile = profile;
    }

    public CharSequence authentication(
            final BybitCredentials credentials, final long expiresMillis) {
        if (credentials == null || expiresMillis <= 0) {
            throw new IllegalArgumentException("credentials and expiry are required");
        }
        signatureInput.setLength(0);
        signatureInput.append("GET/realtime").append(expiresMillis);
        signature.setLength(0);
        credentials.signAscii(signatureInput, signature);
        output.setLength(0);
        output.append("{\"op\":\"auth\",\"args\":[\"");
        credentials.appendApiKey(output);
        output.append("\",").append(expiresMillis).append(",\"").append(signature).append("\"]}");
        return output;
    }

    public CharSequence privateSubscription() {
        output.setLength(0);
        return output.append(
                "{\"op\":\"subscribe\",\"args\":[\"order.inverse\",\"execution.inverse\"]}");
    }

    public CharSequence ping() {
        output.setLength(0);
        return output.append("{\"op\":\"ping\"}");
    }

    public CharSequence command(final MutableOrderCommand command, final long timestampMillis) {
        validate(command, timestampMillis);
        encodeClientId(command.localOrderIdHigh(), command.localOrderIdLow());
        output.setLength(0);
        output.append("{\"reqId\":\"");
        appendClientId(output);
        output.append("\",\"header\":{\"X-BAPI-TIMESTAMP\":\"")
                .append(timestampMillis)
                .append("\",\"X-BAPI-RECV-WINDOW\":\"")
                .append(profile.receiveWindowMillis())
                .append("\"},\"op\":\"");
        if (command.type() == OrderCommandType.SUBMIT) {
            output.append("order.create\",\"args\":[{\"category\":\"")
                    .append(profile.category())
                    .append("\",\"symbol\":\"")
                    .append(profile.symbol())
                    .append("\",\"side\":\"")
                    .append(command.side() == OrderSide.BUY ? "Buy" : "Sell")
                    .append("\",\"orderType\":\"Limit\",\"qty\":\"");
            BybitScaledDecimal.append(output, command.quantity(), profile.quantityScale());
            output.append("\",\"price\":\"");
            BybitScaledDecimal.append(output, command.limitPriceTicks(), profile.priceScale());
            output.append("\",\"timeInForce\":\"IOC\",\"orderLinkId\":\"");
            appendClientId(output);
            output.append("\"}]}");
        } else if (command.type() == OrderCommandType.CANCEL) {
            output.append("order.cancel\",\"args\":[{\"category\":\"")
                    .append(profile.category())
                    .append("\",\"symbol\":\"")
                    .append(profile.symbol())
                    .append("\",\"orderLinkId\":\"");
            appendClientId(output);
            output.append("\"}]}");
        } else {
            throw new IllegalArgumentException("QUERY is reconciliation-only");
        }
        return output;
    }

    public CharSequence cancelAll(final long timestampMillis, final String requestId) {
        if (timestampMillis <= 0
                || requestId == null
                || requestId.isEmpty()
                || requestId.length() > 36) {
            throw new IllegalArgumentException("invalid cancel-all request");
        }
        output.setLength(0);
        return output.append("{\"reqId\":\"")
                .append(requestId)
                .append("\",\"header\":{\"X-BAPI-TIMESTAMP\":\"")
                .append(timestampMillis)
                .append("\",\"X-BAPI-RECV-WINDOW\":\"")
                .append(profile.receiveWindowMillis())
                .append("\"},\"op\":\"order.cancel-all\",\"args\":[{\"category\":\"")
                .append(profile.category())
                .append("\",\"symbol\":\"")
                .append(profile.symbol())
                .append("\"}]}");
    }

    private void validate(final MutableOrderCommand command, final long timestampMillis) {
        if (command == null
                || timestampMillis <= 0
                || command.venueId() != profile.venueId()
                || command.instrumentId() != profile.instrumentId()
                || command.side() == null
                || command.quantity() <= 0
                || command.limitPriceTicks() <= 0) {
            throw new IllegalArgumentException("command does not match Bybit profile");
        }
    }

    private void encodeClientId(final long high, final long low) {
        localId.set(high, low);
        if (VenueClientIdEncoder.encode(localId, clientId, 0) != clientId.length) {
            throw new IllegalStateException("client ID encoding failed");
        }
    }

    private void appendClientId(final StringBuilder destination) {
        for (byte value : clientId) destination.append((char) value);
    }
}
