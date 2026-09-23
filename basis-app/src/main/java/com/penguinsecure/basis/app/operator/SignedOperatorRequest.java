package com.penguinsecure.basis.app.operator;

/** Cold immutable request. Signatures cover every field and bounded payload byte. */
public record SignedOperatorRequest(
        long commandIdHigh,
        long commandIdLow,
        long operatorId,
        OperatorRole role,
        OperatorAction action,
        long expectedConfigurationGeneration,
        long controlGeneration,
        int scopeType,
        int scopeId,
        int reasonCode,
        long issuedEpochNanos,
        long expiresEpochNanos,
        byte[] payload,
        byte[] signature) {
    public SignedOperatorRequest {
        if ((commandIdHigh == 0 && commandIdLow == 0)
                || operatorId <= 0
                || role == null
                || action == null
                || expectedConfigurationGeneration < 0
                || controlGeneration < 0
                || scopeType < 0
                || scopeId < 0
                || reasonCode < 0
                || issuedEpochNanos <= 0
                || expiresEpochNanos <= issuedEpochNanos
                || payload == null
                || signature == null
                || signature.length != 32)
            throw new IllegalArgumentException("invalid operator request");
        payload = payload.clone();
        signature = signature.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }
}
