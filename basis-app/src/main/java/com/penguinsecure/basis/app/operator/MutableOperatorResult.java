package com.penguinsecure.basis.app.operator;

import com.penguinsecure.basis.app.lifecycle.ExecutionCellState;

/** Reusable bounded operator result view. */
public final class MutableOperatorResult {
    private long idHigh, idLow, configurationGeneration, controlGeneration;
    private OperatorCommandStatus status;
    private ExecutionCellState state;

    MutableOperatorResult set(
            final long high,
            final long low,
            final OperatorCommandStatus newStatus,
            final ExecutionCellState newState,
            final long config,
            final long control) {
        idHigh = high;
        idLow = low;
        status = newStatus;
        state = newState;
        configurationGeneration = config;
        controlGeneration = control;
        return this;
    }

    public long idHigh() {
        return idHigh;
    }

    public long idLow() {
        return idLow;
    }

    public OperatorCommandStatus status() {
        return status;
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
}
