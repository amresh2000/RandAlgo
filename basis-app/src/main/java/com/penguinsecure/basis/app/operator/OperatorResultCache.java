package com.penguinsecure.basis.app.operator;

import com.penguinsecure.basis.app.lifecycle.ExecutionCellState;

/** Fixed-capacity idempotency cache. Capacity exhaustion rejects new mutations. */
public final class OperatorResultCache {
    private final long[] high, low, configs, controls;
    private final OperatorCommandStatus[] statuses;
    private final ExecutionCellState[] states;
    private int size;

    public OperatorResultCache(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        high = new long[capacity];
        low = new long[capacity];
        configs = new long[capacity];
        controls = new long[capacity];
        statuses = new OperatorCommandStatus[capacity];
        states = new ExecutionCellState[capacity];
    }

    public int find(final long idHigh, final long idLow) {
        for (int index = 0; index < size; index++)
            if (high[index] == idHigh && low[index] == idLow) return index;
        return -1;
    }

    public boolean put(final MutableOperatorResult result) {
        if (find(result.idHigh(), result.idLow()) >= 0 || size == high.length) return false;
        high[size] = result.idHigh();
        low[size] = result.idLow();
        statuses[size] = result.status();
        states[size] = result.state();
        configs[size] = result.configurationGeneration();
        controls[size] = result.controlGeneration();
        size++;
        return true;
    }

    public MutableOperatorResult copy(final int index, final MutableOperatorResult destination) {
        return destination.set(
                high[index],
                low[index],
                statuses[index],
                states[index],
                configs[index],
                controls[index]);
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return high.length;
    }
}
