package com.penguinsecure.basis.sim.scenario;

public enum ScenarioStepType {
    ADVANCE_TO,
    PUMP,
    STALL_ORDER_AGENT,
    RESUME_ORDER_AGENT,
    STALL_URGENT_QUEUE,
    RESUME_URGENT_QUEUE,
    STALL_FACT_INGRESS,
    RESUME_FACT_INGRESS,
    CHECKPOINT,
    KILL,
    ARCHIVE_FAILURE,
    RECOVER_HEDGE_PATH
}
