package com.penguinsecure.basis.app.operator;

import com.penguinsecure.basis.app.lifecycle.ExecutionCellLifecycle;
import com.penguinsecure.basis.app.lifecycle.LifecycleStatus;
import com.penguinsecure.basis.core.risk.KillHierarchy;
import com.penguinsecure.basis.core.risk.KillScope;
import com.penguinsecure.basis.core.risk.KillUpdateStatus;
import com.penguinsecure.basis.core.time.EpochClock;

/** Core-owned authenticated command processor; audit always precedes mutation. */
public final class OperatorCommandProcessor implements OperatorCommandHandler {
    private final ExecutionCellLifecycle lifecycle;
    private final KillHierarchy kills;
    private final OperatorAuditSink audit;
    private final OperatorActionPort actions;
    private final OperatorResultLane results;
    private final OperatorResultCache cache;
    private final EpochClock clock;
    private final MutableOperatorResult result = new MutableOperatorResult();

    public OperatorCommandProcessor(
            final ExecutionCellLifecycle lifecycle,
            final KillHierarchy kills,
            final OperatorAuditSink audit,
            final OperatorActionPort actions,
            final OperatorResultLane results,
            final OperatorResultCache cache,
            final EpochClock clock) {
        if (lifecycle == null
                || kills == null
                || audit == null
                || actions == null
                || results == null
                || cache == null
                || clock == null) throw new NullPointerException("dependencies are required");
        this.lifecycle = lifecycle;
        this.kills = kills;
        this.audit = audit;
        this.actions = actions;
        this.results = results;
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public void onCommand(final MutableOperatorCommand command) {
        final int prior = cache.find(command.idHigh(), command.idLow());
        if (prior >= 0) {
            cache.copy(prior, result);
            results.publish(result);
            return;
        }
        if (cache.size() == cache.capacity()) {
            result.set(
                    command.idHigh(),
                    command.idLow(),
                    OperatorCommandStatus.CAPACITY_EXHAUSTED,
                    lifecycle.state(),
                    lifecycle.configurationGeneration(),
                    lifecycle.controlGeneration());
            results.publish(result);
            return;
        }
        OperatorCommandStatus status;
        if (clock.epochNanos() > command.expiresEpochNanos())
            status = OperatorCommandStatus.EXPIRED;
        else if (!command.action().authorized(command.role()))
            status = OperatorCommandStatus.UNAUTHORIZED;
        else if (command.action().mutating() && !audit.audit(command))
            status = OperatorCommandStatus.AUDIT_FAILED;
        else status = apply(command);
        result.set(
                command.idHigh(),
                command.idLow(),
                status,
                lifecycle.state(),
                lifecycle.configurationGeneration(),
                lifecycle.controlGeneration());
        cache.put(result);
        results.publish(result);
    }

    private OperatorCommandStatus apply(final MutableOperatorCommand command) {
        return switch (command.action()) {
            case STATUS -> OperatorCommandStatus.APPLIED;
            case ARM ->
                    lifecycle(
                            lifecycle.arm(
                                    command.expectedConfigurationGeneration(),
                                    command.controlGeneration()));
            case DISARM -> lifecycle(lifecycle.disarm(command.controlGeneration()));
            case KILL -> kill(command);
            case CANCEL_ALL -> effect(actions.cancelAll(command.scopeType(), command.scopeId()));
            case RECONCILE -> effect(actions.reconcile(command.scopeType(), command.scopeId()));
            case SNAPSHOT -> effect(actions.snapshot());
            case STAGE_CONFIG ->
                    effect(
                            actions.stageConfiguration(
                                    command.payload(),
                                    command.payloadLength(),
                                    command.expectedConfigurationGeneration()));
            case ACTIVATE_CONFIG ->
                    effect(
                            actions.activateConfiguration(
                                    command.expectedConfigurationGeneration()));
            case SHUTDOWN -> effect(actions.beginShutdown(command.controlGeneration()));
        };
    }

    private OperatorCommandStatus kill(final MutableOperatorCommand command) {
        if (command.scopeType() >= KillScope.values().length) return OperatorCommandStatus.INVALID;
        final KillUpdateStatus status =
                kills.kill(
                        KillScope.values()[command.scopeType()],
                        command.scopeId(),
                        command.controlGeneration());
        return switch (status) {
            case APPLIED, IDEMPOTENT -> OperatorCommandStatus.APPLIED;
            case STALE_GENERATION -> OperatorCommandStatus.STALE_GENERATION;
            default -> OperatorCommandStatus.INVALID;
        };
    }

    private static OperatorCommandStatus effect(final boolean success) {
        return success ? OperatorCommandStatus.APPLIED : OperatorCommandStatus.ACTION_FAILED;
    }

    private static OperatorCommandStatus lifecycle(final LifecycleStatus status) {
        return switch (status) {
            case APPLIED, IDEMPOTENT -> OperatorCommandStatus.APPLIED;
            case NOT_READY -> OperatorCommandStatus.NOT_READY;
            case STALE_GENERATION -> OperatorCommandStatus.STALE_GENERATION;
            default -> OperatorCommandStatus.ACTION_FAILED;
        };
    }
}
