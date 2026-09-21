package com.penguinsecure.basis.sim.scheduler;

import com.penguinsecure.basis.sim.time.VirtualClock;
import java.util.Arrays;

/** Fixed-capacity event heap with a documented total order for equal virtual times. */
public final class DeterministicScheduler {
    private final VirtualClock clock;
    private final long[] times;
    private final int[] priorities;
    private final int[] producerIds;
    private final long[] producerSequences;
    private final int[] kinds;
    private final long[][] values;
    private final long[] lastProducerSequences;
    private int size;
    private long executedEvents;

    public DeterministicScheduler(
            final int capacity, final int producerCapacity, final VirtualClock clock) {
        if (capacity <= 0 || producerCapacity <= 0 || clock == null) {
            throw new IllegalArgumentException("invalid scheduler configuration");
        }
        this.clock = clock;
        times = new long[capacity];
        priorities = new int[capacity];
        producerIds = new int[capacity];
        producerSequences = new long[capacity];
        kinds = new int[capacity];
        values =
                new long[][] {
                    new long[capacity], new long[capacity], new long[capacity], new long[capacity]
                };
        lastProducerSequences = new long[producerCapacity];
        Arrays.fill(lastProducerSequences, -1);
    }

    @SuppressWarnings("ParameterNumber")
    public ScheduleStatus schedule(
            final long scheduledMonoNanos,
            final int sourcePriority,
            final int producerId,
            final long producerSequence,
            final int eventKind,
            final long value0,
            final long value1,
            final long value2,
            final long value3) {
        if (scheduledMonoNanos < clock.nanoTime()
                || sourcePriority < 0
                || producerId < 0
                || producerId >= lastProducerSequences.length
                || producerSequence < 0
                || eventKind <= 0) return ScheduleStatus.INVALID_ARGUMENT;
        if (producerSequence <= lastProducerSequences[producerId]) {
            return ScheduleStatus.NON_MONOTONIC_PRODUCER_SEQUENCE;
        }
        if (size == times.length) return ScheduleStatus.CAPACITY_EXHAUSTED;
        final int index = size++;
        times[index] = scheduledMonoNanos;
        priorities[index] = sourcePriority;
        producerIds[index] = producerId;
        producerSequences[index] = producerSequence;
        kinds[index] = eventKind;
        values[0][index] = value0;
        values[1][index] = value1;
        values[2][index] = value2;
        values[3][index] = value3;
        lastProducerSequences[producerId] = producerSequence;
        siftUp(index);
        return ScheduleStatus.OK;
    }

    public boolean runNext(final SimulationEventHandler handler) {
        if (handler == null) throw new NullPointerException("handler is required");
        if (size == 0) return false;
        final long time = times[0];
        final int priority = priorities[0];
        final int producer = producerIds[0];
        final long sequence = producerSequences[0];
        final int kind = kinds[0];
        final long value0 = values[0][0];
        final long value1 = values[1][0];
        final long value2 = values[2][0];
        final long value3 = values[3][0];
        removeRoot();
        clock.advanceTo(time);
        handler.onEvent(time, priority, producer, sequence, kind, value0, value1, value2, value3);
        executedEvents++;
        return true;
    }

    public RunStatus runToQuiescence(
            final int maximumEvents,
            final long deadlineMonoNanos,
            final SimulationEventHandler handler) {
        if (maximumEvents <= 0 || deadlineMonoNanos < clock.nanoTime() || handler == null) {
            throw new IllegalArgumentException("invalid run bounds");
        }
        int count = 0;
        while (size > 0) {
            if (count == maximumEvents) return RunStatus.EVENT_LIMIT_REACHED;
            if (times[0] > deadlineMonoNanos) return RunStatus.DEADLINE_REACHED;
            runNext(handler);
            count++;
        }
        return RunStatus.QUIESCENT;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return times.length;
    }

    public long executedEvents() {
        return executedEvents;
    }

    public long nextScheduledMonoNanos() {
        return size == 0 ? 0 : times[0];
    }

    private void removeRoot() {
        final int last = --size;
        if (last == 0) return;
        copy(last, 0);
        siftDown(0);
    }

    private void siftUp(int index) {
        while (index > 0) {
            final int parent = (index - 1) >>> 1;
            if (!less(index, parent)) return;
            swap(index, parent);
            index = parent;
        }
    }

    private void siftDown(int index) {
        while (true) {
            final int left = index * 2 + 1;
            if (left >= size) return;
            final int right = left + 1;
            final int child = right < size && less(right, left) ? right : left;
            if (!less(child, index)) return;
            swap(child, index);
            index = child;
        }
    }

    private boolean less(final int left, final int right) {
        if (times[left] != times[right]) return times[left] < times[right];
        if (priorities[left] != priorities[right]) return priorities[left] < priorities[right];
        if (producerIds[left] != producerIds[right]) return producerIds[left] < producerIds[right];
        return producerSequences[left] < producerSequences[right];
    }

    private void swap(final int left, final int right) {
        final long time = times[left];
        final int priority = priorities[left];
        final int producer = producerIds[left];
        final long sequence = producerSequences[left];
        final int kind = kinds[left];
        final long value0 = values[0][left];
        final long value1 = values[1][left];
        final long value2 = values[2][left];
        final long value3 = values[3][left];
        copy(right, left);
        times[right] = time;
        priorities[right] = priority;
        producerIds[right] = producer;
        producerSequences[right] = sequence;
        kinds[right] = kind;
        values[0][right] = value0;
        values[1][right] = value1;
        values[2][right] = value2;
        values[3][right] = value3;
    }

    private void copy(final int source, final int destination) {
        times[destination] = times[source];
        priorities[destination] = priorities[source];
        producerIds[destination] = producerIds[source];
        producerSequences[destination] = producerSequences[source];
        kinds[destination] = kinds[source];
        for (int field = 0; field < values.length; field++) {
            values[field][destination] = values[field][source];
        }
    }
}
