package com.penguinsecure.basis.app.operator;

import com.penguinsecure.basis.app.lifecycle.ExecutionCellState;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Fixed SPSC result lane; a slow client drops results but never blocks core work. */
public final class OperatorResultLane {
    private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);
    private final int capacity;
    private final long[] high, low, configs, controls, published;
    private final int[] statuses, states;
    private final MutableOperatorResult view = new MutableOperatorResult();
    private volatile long producerSequence, consumerSequence, dropped;

    public OperatorResultLane(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        high = new long[capacity];
        low = new long[capacity];
        configs = new long[capacity];
        controls = new long[capacity];
        published = new long[capacity];
        statuses = new int[capacity];
        states = new int[capacity];
    }

    public boolean publish(final MutableOperatorResult result) {
        if (producerSequence - consumerSequence >= capacity) {
            dropped++;
            return false;
        }
        final int index = (int) (producerSequence % capacity);
        high[index] = result.idHigh();
        low[index] = result.idLow();
        configs[index] = result.configurationGeneration();
        controls[index] = result.controlGeneration();
        statuses[index] = result.status().ordinal();
        states[index] = result.state().ordinal();
        LONGS.setRelease(published, index, producerSequence + 1);
        producerSequence++;
        return true;
    }

    public int drain(final OperatorResultHandler handler, final int limit) {
        if (handler == null || limit <= 0) return 0;
        int count = 0;
        while (count < limit && consumerSequence < producerSequence) {
            final int index = (int) (consumerSequence % capacity);
            if ((long) LONGS.getAcquire(published, index) != consumerSequence + 1) break;
            view.set(
                    high[index],
                    low[index],
                    OperatorCommandStatus.values()[statuses[index]],
                    ExecutionCellState.values()[states[index]],
                    configs[index],
                    controls[index]);
            handler.onResult(view);
            LONGS.setRelease(published, index, 0L);
            consumerSequence++;
            count++;
        }
        return count;
    }

    public int size() {
        return (int) (producerSequence - consumerSequence);
    }

    public long dropped() {
        return dropped;
    }
}
