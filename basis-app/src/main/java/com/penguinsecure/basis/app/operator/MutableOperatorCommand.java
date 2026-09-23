package com.penguinsecure.basis.app.operator;

/** Reusable hot-path command view populated by the bounded operator lane. */
public final class MutableOperatorCommand {
    private long idHigh,
            idLow,
            operatorId,
            expectedConfiguration,
            controlGeneration,
            issued,
            expires;
    private OperatorRole role;
    private OperatorAction action;
    private int scopeType, scopeId, reasonCode, payloadLength;
    private byte[] payload;

    MutableOperatorCommand(final int maximumPayload) {
        payload = new byte[maximumPayload];
    }

    @SuppressWarnings("ParameterNumber")
    MutableOperatorCommand set(
            final long high,
            final long low,
            final long operator,
            final OperatorRole newRole,
            final OperatorAction newAction,
            final long expectedConfig,
            final long generation,
            final int newScopeType,
            final int newScopeId,
            final int newReason,
            final long newIssued,
            final long newExpires,
            final byte[] source,
            final int offset,
            final int length) {
        idHigh = high;
        idLow = low;
        operatorId = operator;
        role = newRole;
        action = newAction;
        expectedConfiguration = expectedConfig;
        controlGeneration = generation;
        scopeType = newScopeType;
        scopeId = newScopeId;
        reasonCode = newReason;
        issued = newIssued;
        expires = newExpires;
        System.arraycopy(source, offset, payload, 0, length);
        payloadLength = length;
        return this;
    }

    public long idHigh() {
        return idHigh;
    }

    public long idLow() {
        return idLow;
    }

    public long operatorId() {
        return operatorId;
    }

    public OperatorRole role() {
        return role;
    }

    public OperatorAction action() {
        return action;
    }

    public long expectedConfigurationGeneration() {
        return expectedConfiguration;
    }

    public long controlGeneration() {
        return controlGeneration;
    }

    public int scopeType() {
        return scopeType;
    }

    public int scopeId() {
        return scopeId;
    }

    public int reasonCode() {
        return reasonCode;
    }

    public long issuedEpochNanos() {
        return issued;
    }

    public long expiresEpochNanos() {
        return expires;
    }

    public byte[] payload() {
        return payload;
    }

    public int payloadLength() {
        return payloadLength;
    }
}
