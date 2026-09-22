package com.penguinsecure.basis.sim.scenario;

/** Immutable primitive scenario produced by {@link ScenarioBuilder}. */
public final class SimulationScenario {
    private final String name;
    private final ScenarioStepType[] types;
    private final long[] values;
    private final int size;

    SimulationScenario(
            final String name,
            final ScenarioStepType[] sourceTypes,
            final long[] sourceValues,
            final int size) {
        this.name = name;
        types = java.util.Arrays.copyOf(sourceTypes, size);
        values = java.util.Arrays.copyOf(sourceValues, size);
        this.size = size;
    }

    public String name() {
        return name;
    }

    public int size() {
        return size;
    }

    public ScenarioStepType type(final int index) {
        return types[index];
    }

    public long value(final int index) {
        return values[index];
    }
}
