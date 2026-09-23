package com.penguinsecure.basis.app.lifecycle;

/** Single-owner, generation-fenced arming and shutdown state machine. */
public final class ExecutionCellLifecycle {
    private ExecutionCellState state = ExecutionCellState.BOOT;
    private long configurationGeneration;
    private long controlGeneration;

    public LifecycleStatus beginRecovery() {
        if (state == ExecutionCellState.RECOVERING) return LifecycleStatus.IDEMPOTENT;
        if (state != ExecutionCellState.BOOT) return LifecycleStatus.INVALID_STATE;
        state = ExecutionCellState.RECOVERING;
        return LifecycleStatus.APPLIED;
    }

    public LifecycleStatus observeStartup(final StartupEvidence evidence) {
        if (evidence == null) return LifecycleStatus.INVALID_ARGUMENT;
        if (state != ExecutionCellState.RECOVERING) return LifecycleStatus.INVALID_STATE;
        if (!evidence.ready()) return LifecycleStatus.NOT_READY;
        if (evidence.configurationGeneration() < configurationGeneration)
            return LifecycleStatus.STALE_GENERATION;
        configurationGeneration = evidence.configurationGeneration();
        state = ExecutionCellState.DISARMED_READY;
        return LifecycleStatus.APPLIED;
    }

    public LifecycleStatus arm(final long expectedConfiguration, final long generation) {
        if (generation <= 0 || expectedConfiguration <= 0) return LifecycleStatus.INVALID_ARGUMENT;
        if (generation <= controlGeneration)
            return generation == controlGeneration && state == ExecutionCellState.ARMED
                    ? LifecycleStatus.IDEMPOTENT
                    : LifecycleStatus.STALE_GENERATION;
        if (state != ExecutionCellState.DISARMED_READY) return LifecycleStatus.INVALID_STATE;
        if (expectedConfiguration != configurationGeneration)
            return LifecycleStatus.STALE_GENERATION;
        controlGeneration = generation;
        state = ExecutionCellState.ARMED;
        return LifecycleStatus.APPLIED;
    }

    public LifecycleStatus disarm(final long generation) {
        if (generation <= 0) return LifecycleStatus.INVALID_ARGUMENT;
        if (generation <= controlGeneration)
            return state == ExecutionCellState.DISARMED_READY && generation == controlGeneration
                    ? LifecycleStatus.IDEMPOTENT
                    : LifecycleStatus.STALE_GENERATION;
        if (state != ExecutionCellState.ARMED && state != ExecutionCellState.DISARMED_READY)
            return LifecycleStatus.INVALID_STATE;
        controlGeneration = generation;
        state = ExecutionCellState.DISARMED_READY;
        return LifecycleStatus.APPLIED;
    }

    public LifecycleStatus beginDrain(final long generation) {
        if (generation <= controlGeneration) return LifecycleStatus.STALE_GENERATION;
        if (state != ExecutionCellState.ARMED && state != ExecutionCellState.DISARMED_READY)
            return LifecycleStatus.INVALID_STATE;
        controlGeneration = generation;
        state = ExecutionCellState.DRAINING;
        return LifecycleStatus.APPLIED;
    }

    public LifecycleStatus validateConfigurationActivation(final long generation) {
        if (generation <= 0) return LifecycleStatus.INVALID_ARGUMENT;
        if (generation <= configurationGeneration) return LifecycleStatus.STALE_GENERATION;
        if (state != ExecutionCellState.DISARMED_READY) return LifecycleStatus.INVALID_STATE;
        return LifecycleStatus.APPLIED;
    }

    public LifecycleStatus configurationActivated(final long generation) {
        final LifecycleStatus status = validateConfigurationActivation(generation);
        if (status != LifecycleStatus.APPLIED) return status;
        configurationGeneration = generation;
        return LifecycleStatus.APPLIED;
    }

    public void transitionShutdown(final ExecutionCellState next) {
        if (!legalShutdown(state, next))
            throw new IllegalStateException("illegal shutdown transition " + state + " -> " + next);
        state = next;
    }

    public void emergencyRequired() {
        state = ExecutionCellState.EMERGENCY_REQUIRED;
    }

    public void fail() {
        state = ExecutionCellState.FAILED;
    }

    public ExecutionCellState state() {
        return state;
    }

    public long configurationGeneration() {
        return configurationGeneration;
    }

    public long controlGeneration() {
        return controlGeneration;
    }

    public boolean armed() {
        return state == ExecutionCellState.ARMED;
    }

    private static boolean legalShutdown(
            final ExecutionCellState from, final ExecutionCellState to) {
        return switch (from) {
            case DRAINING -> to == ExecutionCellState.RECONCILING;
            case RECONCILING -> to == ExecutionCellState.SNAPSHOTTING;
            case SNAPSHOTTING -> to == ExecutionCellState.FLUSHING_JOURNAL;
            case FLUSHING_JOURNAL -> to == ExecutionCellState.CLOSING_VENUES;
            case CLOSING_VENUES -> to == ExecutionCellState.CLOSING_INFRASTRUCTURE;
            case CLOSING_INFRASTRUCTURE -> to == ExecutionCellState.STOPPED;
            default -> false;
        };
    }
}
