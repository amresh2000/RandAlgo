package com.penguinsecure.basis.sim.scheduler;

/** Callback for one scheduled primitive event. */
@FunctionalInterface
public interface SimulationEventHandler {
    @SuppressWarnings("ParameterNumber")
    void onEvent(
            long scheduledMonoNanos,
            int sourcePriority,
            int producerId,
            long producerSequence,
            int eventKind,
            long value0,
            long value1,
            long value2,
            long value3);
}
