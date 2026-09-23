package com.penguinsecure.basis.app.assembly;

import com.penguinsecure.basis.app.config.ConfigurationStageStatus;
import com.penguinsecure.basis.app.config.SignedConfiguration;
import com.penguinsecure.basis.app.config.SignedConfigurationCodec;
import com.penguinsecure.basis.app.config.StagedConfigurationStore;
import com.penguinsecure.basis.app.lifecycle.ExecutionCellLifecycle;
import com.penguinsecure.basis.app.lifecycle.LifecycleStatus;
import com.penguinsecure.basis.app.operator.OperatorActionPort;
import com.penguinsecure.basis.core.time.EpochClock;

/** Concrete operator effects with signed configuration and shutdown coordination. */
public final class ExecutionCellActionPort implements OperatorActionPort {
    private final OperationalEffects effects;
    private final StagedConfigurationStore configurations;
    private final ShutdownCoordinator shutdown;
    private final ExecutionCellLifecycle lifecycle;
    private final EpochClock clock;

    public ExecutionCellActionPort(
            final OperationalEffects effects,
            final StagedConfigurationStore configurations,
            final ExecutionCellLifecycle lifecycle,
            final ShutdownCoordinator shutdown,
            final EpochClock clock) {
        if (effects == null
                || configurations == null
                || lifecycle == null
                || shutdown == null
                || clock == null) {
            throw new NullPointerException("dependencies are required");
        }
        this.effects = effects;
        this.configurations = configurations;
        this.lifecycle = lifecycle;
        this.shutdown = shutdown;
        this.clock = clock;
    }

    @Override
    public boolean cancelAll(final int scopeType, final int scopeId) {
        return effects.cancelAll(scopeType, scopeId);
    }

    @Override
    public boolean reconcile(final int scopeType, final int scopeId) {
        return effects.reconcile(scopeType, scopeId);
    }

    @Override
    public boolean snapshot() {
        return effects.snapshot();
    }

    @Override
    public boolean stageConfiguration(
            final byte[] payload, final int length, final long generation) {
        final SignedConfiguration configuration = SignedConfigurationCodec.decode(payload, length);
        return configuration != null
                && configuration.generation() == generation
                && configurations.stage(configuration, clock.epochNanos())
                        == ConfigurationStageStatus.STAGED;
    }

    @Override
    public boolean activateConfiguration(final long generation) {
        if (lifecycle.validateConfigurationActivation(generation) != LifecycleStatus.APPLIED) {
            return false;
        }
        if (configurations.activate(generation) != ConfigurationStageStatus.ACTIVATED) {
            return false;
        }
        return lifecycle.configurationActivated(generation) == LifecycleStatus.APPLIED;
    }

    @Override
    public boolean beginShutdown(final long generation) {
        return shutdown.begin(generation);
    }
}
