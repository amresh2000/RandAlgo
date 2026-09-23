package com.penguinsecure.basis.app.config;

/** Single-owner stage/activate slot; generations only move forward. */
public final class StagedConfigurationStore {
    private final ConfigurationSignatureVerifier verifier;
    private SignedConfiguration staged;
    private long activeGeneration;

    public StagedConfigurationStore(final ConfigurationSignatureVerifier verifier) {
        if (verifier == null) throw new NullPointerException("verifier is required");
        this.verifier = verifier;
    }

    public ConfigurationStageStatus stage(
            final SignedConfiguration configuration, final long nowEpochNanos) {
        if (!verifier.verify(configuration, nowEpochNanos))
            return ConfigurationStageStatus.INVALID_SIGNATURE;
        if (configuration.generation() <= activeGeneration
                || staged != null && configuration.generation() <= staged.generation())
            return ConfigurationStageStatus.STALE_GENERATION;
        staged = configuration;
        return ConfigurationStageStatus.STAGED;
    }

    public ConfigurationStageStatus activate(final long expectedGeneration) {
        if (staged == null) return ConfigurationStageStatus.NOTHING_STAGED;
        if (staged.generation() != expectedGeneration || expectedGeneration <= activeGeneration)
            return ConfigurationStageStatus.STALE_GENERATION;
        activeGeneration = expectedGeneration;
        staged = null;
        return ConfigurationStageStatus.ACTIVATED;
    }

    public long activeGeneration() {
        return activeGeneration;
    }

    public long stagedGeneration() {
        return staged == null ? 0 : staged.generation();
    }
}
