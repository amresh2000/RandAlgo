package com.penguinsecure.basis.app.operator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Closeable cold-path authenticator for one operator identity and maximum role. */
public final class OperatorAuthenticator implements AutoCloseable {
    private final long operatorId;
    private final OperatorRole maximumRole;
    private byte[] key;

    public OperatorAuthenticator(
            final long operatorId, final OperatorRole maximumRole, final byte[] key) {
        if (operatorId <= 0
                || maximumRole == null
                || key == null
                || key.length < 16
                || key.length > 256)
            throw new IllegalArgumentException("invalid operator credential");
        this.operatorId = operatorId;
        this.maximumRole = maximumRole;
        this.key = key.clone();
    }

    public boolean verify(final SignedOperatorRequest request, final long nowEpochNanos) {
        if (request == null
                || key == null
                || request.operatorId() != operatorId
                || request.role().authority() > maximumRole.authority()
                || nowEpochNanos < request.issuedEpochNanos()
                || nowEpochNanos > request.expiresEpochNanos()) return false;
        final byte[] actual = sign(request);
        final boolean valid = java.security.MessageDigest.isEqual(actual, request.signature());
        Arrays.fill(actual, (byte) 0);
        return valid;
    }

    public byte[] sign(final SignedOperatorRequest request) {
        if (key == null) throw new IllegalStateException("authenticator is closed");
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            final ByteBuffer header = ByteBuffer.allocate(88).order(ByteOrder.LITTLE_ENDIAN);
            header.putLong(request.commandIdHigh())
                    .putLong(request.commandIdLow())
                    .putLong(request.operatorId())
                    .putInt(request.role().ordinal())
                    .putInt(request.action().ordinal())
                    .putLong(request.expectedConfigurationGeneration())
                    .putLong(request.controlGeneration())
                    .putInt(request.scopeType())
                    .putInt(request.scopeId())
                    .putInt(request.reasonCode())
                    .putInt(request.payload().length)
                    .putLong(request.issuedEpochNanos())
                    .putLong(request.expiresEpochNanos())
                    .putLong(0);
            mac.update(header.array());
            return mac.doFinal(request.payload());
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    @Override
    public void close() {
        if (key != null) Arrays.fill(key, (byte) 0);
        key = null;
    }

    @Override
    public String toString() {
        return "OperatorAuthenticator[REDACTED]";
    }
}
