package com.penguinsecure.basis.sim.scenario;

/** Code-native checked scenario DSL with explicit declaration order. */
public final class ScenarioBuilder {
    private final String name;
    private final ScenarioStepType[] types;
    private final long[] values;
    private int size;

    public ScenarioBuilder(final String name, final int maximumSteps) {
        if (name == null || name.isBlank() || maximumSteps <= 0) {
            throw new IllegalArgumentException("invalid scenario");
        }
        this.name = name;
        types = new ScenarioStepType[maximumSteps];
        values = new long[maximumSteps];
    }

    public ScenarioBuilder advanceTo(final long monoNanos) {
        return add(ScenarioStepType.ADVANCE_TO, monoNanos);
    }

    public ScenarioBuilder pump(final int maximumRounds) {
        return add(ScenarioStepType.PUMP, maximumRounds);
    }

    public ScenarioBuilder stallOrderAgent() {
        return add(ScenarioStepType.STALL_ORDER_AGENT, 0);
    }

    public ScenarioBuilder resumeOrderAgent() {
        return add(ScenarioStepType.RESUME_ORDER_AGENT, 0);
    }

    public ScenarioBuilder stallUrgentQueue() {
        return add(ScenarioStepType.STALL_URGENT_QUEUE, 0);
    }

    public ScenarioBuilder resumeUrgentQueue() {
        return add(ScenarioStepType.RESUME_URGENT_QUEUE, 0);
    }

    public ScenarioBuilder stallFactIngress() {
        return add(ScenarioStepType.STALL_FACT_INGRESS, 0);
    }

    public ScenarioBuilder resumeFactIngress() {
        return add(ScenarioStepType.RESUME_FACT_INGRESS, 0);
    }

    public ScenarioBuilder checkpoint(final int invariantId) {
        return add(ScenarioStepType.CHECKPOINT, invariantId);
    }

    public ScenarioBuilder kill(final int scopeId) {
        return add(ScenarioStepType.KILL, scopeId);
    }

    public ScenarioBuilder archiveFailure(final int archiveId) {
        return add(ScenarioStepType.ARCHIVE_FAILURE, archiveId);
    }

    public ScenarioBuilder recoverHedgePath() {
        return add(ScenarioStepType.RECOVER_HEDGE_PATH, 0);
    }

    public SimulationScenario build() {
        if (size == 0) throw new IllegalStateException("scenario has no steps");
        return new SimulationScenario(name, types, values, size);
    }

    private ScenarioBuilder add(final ScenarioStepType type, final long value) {
        if (size == types.length) throw new IllegalStateException("scenario capacity exhausted");
        types[size] = type;
        values[size] = value;
        size++;
        return this;
    }
}
