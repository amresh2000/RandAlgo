package com.penguinsecure.basis.app.config;

/** Defensive signed configuration envelope accepted only on the cold path. */
public record SignedConfiguration(
        int formatVersion,
        long generation,
        long issuedEpochNanos,
        long expiresEpochNanos,
        byte[] payload,
        byte[] signature) {
    public SignedConfiguration {
        if (formatVersion <= 0
                || generation <= 0
                || issuedEpochNanos <= 0
                || expiresEpochNanos <= issuedEpochNanos
                || payload == null
                || payload.length == 0
                || signature == null
                || signature.length != 32)
            throw new IllegalArgumentException("invalid signed configuration");
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
