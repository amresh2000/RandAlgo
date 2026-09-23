package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.identity.VenueClientIdEncoder;

/** Reusable deterministic Deribit JSON-RPC v2 request encoder. */
public final class DeribitOrderRequestEncoder {
    private final DeribitOrderProfile profile;
    private final byte[] label = new byte[VenueClientIdEncoder.ENCODED_LENGTH];
    private final MutableLocalOrderId localId = new MutableLocalOrderId();
    private final StringBuilder output = new StringBuilder(768);
    private final StringBuilder signatureInput = new StringBuilder(256);
    private final StringBuilder signature = new StringBuilder(64);

    public DeribitOrderRequestEncoder(final DeribitOrderProfile profile) {
        if (profile == null) throw new NullPointerException("profile is required");
        this.profile = profile;
    }

    public CharSequence authentication(
            final long requestId,
            final long timestampMillis,
            final CharSequence nonce,
            final DeribitCredentials credentials) {
        if (requestId <= 0
                || timestampMillis <= 0
                || !safeAscii(nonce, 8, 128)
                || credentials == null)
            throw new IllegalArgumentException("invalid authentication request");
        signatureInput.setLength(0);
        signatureInput.append(timestampMillis).append('\n').append(nonce).append('\n');
        signature.setLength(0);
        credentials.signAscii(signatureInput, signature);
        begin(requestId, "public/auth");
        output.append("\"grant_type\":\"client_signature\",\"client_id\":\"");
        credentials.appendClientId(output);
        output.append("\",\"timestamp\":")
                .append(timestampMillis)
                .append(",\"signature\":\"")
                .append(signature)
                .append("\",\"nonce\":\"")
                .append(nonce)
                .append("\",\"data\":\"\"}}");
        clear(signatureInput);
        clear(signature);
        return output;
    }

    public CharSequence refresh(final long requestId, final DeribitTokenState tokens) {
        if (requestId <= 0 || tokens == null || !tokens.available())
            throw new IllegalArgumentException("invalid refresh request");
        begin(requestId, "public/auth");
        output.append("\"grant_type\":\"refresh_token\",\"refresh_token\":\"");
        tokens.appendRefreshToken(output);
        return output.append("\"}}");
    }

    public CharSequence heartbeat(final long requestId, final int intervalSeconds) {
        if (requestId <= 0 || intervalSeconds < 10)
            throw new IllegalArgumentException("invalid heartbeat request");
        begin(requestId, "public/set_heartbeat");
        return output.append("\"interval\":").append(intervalSeconds).append("}}");
    }

    public CharSequence test(final long requestId) {
        begin(requestId, "public/test");
        return output.append("}}");
    }

    public CharSequence privateSubscription(final long requestId) {
        begin(requestId, "private/subscribe");
        return output.append("\"channels\":[\"user.orders.")
                .append(profile.instrument())
                .append(".raw\",\"user.trades.")
                .append(profile.instrument())
                .append(".raw\",\"user.portfolio.")
                .append(profile.currency())
                .append("\"]}}");
    }

    public CharSequence submit(final MutableOrderCommand command, final long requestId) {
        validate(command, requestId);
        if (command.type() != OrderCommandType.SUBMIT)
            throw new IllegalArgumentException("submit command required");
        encodeLabel(command.localOrderIdHigh(), command.localOrderIdLow());
        begin(requestId, command.side() == OrderSide.BUY ? "private/buy" : "private/sell");
        output.append("\"instrument_name\":\"")
                .append(profile.instrument())
                .append("\",\"amount\":");
        DeribitScaledDecimal.append(output, command.quantity(), profile.amountScale());
        output.append(",\"type\":\"limit\",\"price\":");
        DeribitScaledDecimal.append(output, command.limitPriceTicks(), profile.priceScale());
        output.append(",\"time_in_force\":\"immediate_or_cancel\",\"label\":\"");
        appendLabel();
        return output.append("\"}}");
    }

    public CharSequence cancel(final long requestId, final CharSequence venueOrderId) {
        if (requestId <= 0 || !safeAscii(venueOrderId, 1, 128))
            throw new IllegalArgumentException("invalid cancel request");
        begin(requestId, "private/cancel");
        return output.append("\"order_id\":\"").append(venueOrderId).append("\"}}");
    }

    @SuppressWarnings("ParameterNumber")
    public CharSequence edit(
            final long requestId,
            final CharSequence venueOrderId,
            final long amount,
            final long price,
            final boolean postOnly,
            final boolean rejectPostOnly,
            final boolean reduceOnly) {
        if (requestId <= 0 || !safeAscii(venueOrderId, 1, 128) || amount <= 0 || price <= 0)
            throw new IllegalArgumentException("invalid edit request");
        begin(requestId, "private/edit");
        output.append("\"order_id\":\"").append(venueOrderId).append("\",\"amount\":");
        DeribitScaledDecimal.append(output, amount, profile.amountScale());
        output.append(",\"price\":");
        DeribitScaledDecimal.append(output, price, profile.priceScale());
        return output.append(",\"post_only\":")
                .append(postOnly)
                .append(",\"reject_post_only\":")
                .append(rejectPostOnly)
                .append(",\"reduce_only\":")
                .append(reduceOnly)
                .append("}}");
    }

    public CharSequence cancelAll(final long requestId) {
        begin(requestId, "private/cancel_all_by_instrument");
        return output.append("\"instrument_name\":\"").append(profile.instrument()).append("\"}}");
    }

    public CharSequence cancelOnDisconnect(final long requestId, final boolean enabled) {
        begin(
                requestId,
                enabled
                        ? "private/enable_cancel_on_disconnect"
                        : "private/disable_cancel_on_disconnect");
        return output.append("\"scope\":\"connection\"}}");
    }

    /** Erases the reusable request buffer after a request containing a refresh token is sent. */
    public void clearSensitiveOutput() {
        clear(output);
    }

    private void begin(final long requestId, final String method) {
        if (requestId <= 0) throw new IllegalArgumentException("requestId must be positive");
        output.setLength(0);
        output.append("{\"jsonrpc\":\"2.0\",\"id\":")
                .append(requestId)
                .append(",\"method\":\"")
                .append(method)
                .append("\",\"params\":{");
    }

    private void validate(final MutableOrderCommand command, final long requestId) {
        if (command == null
                || requestId <= 0
                || command.venueId() != profile.venueId()
                || command.instrumentId() != profile.instrumentId()
                || command.side() == null
                || command.quantity() <= 0
                || command.limitPriceTicks() <= 0)
            throw new IllegalArgumentException("command does not match Deribit profile");
    }

    private void encodeLabel(final long high, final long low) {
        localId.set(high, low);
        if (VenueClientIdEncoder.encode(localId, label, 0) != label.length)
            throw new IllegalStateException("label encoding failed");
    }

    private void appendLabel() {
        for (byte value : label) output.append((char) value);
    }

    private static boolean safeAscii(
            final CharSequence value, final int minimum, final int maximum) {
        if (value == null || value.length() < minimum || value.length() > maximum) return false;
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e || character == '"' || character == '\\')
                return false;
        }
        return true;
    }

    private static void clear(final StringBuilder value) {
        for (int index = 0; index < value.length(); index++) value.setCharAt(index, '\0');
        value.setLength(0);
    }
}
