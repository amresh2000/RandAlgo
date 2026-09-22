package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.core.command.OrderCommandType;

/** Fixed-capacity session-fenced table for outstanding trade requests. */
public final class BybitRequestCorrelationTable {
    private final long[] high;
    private final long[] low;
    private final long[] sessionGenerations;
    private final OrderCommandType[] types;
    private final boolean[] active;
    private int size;

    public BybitRequestCorrelationTable(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        high = new long[capacity];
        low = new long[capacity];
        sessionGenerations = new long[capacity];
        types = new OrderCommandType[capacity];
        active = new boolean[capacity];
    }

    public boolean register(
            final long idHigh,
            final long idLow,
            final long sessionGeneration,
            final OrderCommandType type) {
        if ((idHigh == 0 && idLow == 0) || sessionGeneration <= 0 || type == null) return false;
        if (find(idHigh, idLow) >= 0) return false;
        for (int index = 0; index < active.length; index++) {
            if (!active[index]) {
                high[index] = idHigh;
                low[index] = idLow;
                sessionGenerations[index] = sessionGeneration;
                types[index] = type;
                active[index] = true;
                size++;
                return true;
            }
        }
        return false;
    }

    public int find(final long idHigh, final long idLow) {
        for (int index = 0; index < active.length; index++) {
            if (active[index] && high[index] == idHigh && low[index] == idLow) return index;
        }
        return -1;
    }

    public boolean complete(
            final long idHigh, final long idLow, final long expectedSessionGeneration) {
        final int index = find(idHigh, idLow);
        if (index < 0 || sessionGenerations[index] != expectedSessionGeneration) return false;
        active[index] = false;
        types[index] = null;
        size--;
        return true;
    }

    public int clearSession(final long sessionGeneration) {
        int cleared = 0;
        for (int index = 0; index < active.length; index++) {
            if (active[index] && sessionGenerations[index] == sessionGeneration) {
                active[index] = false;
                types[index] = null;
                size--;
                cleared++;
            }
        }
        return cleared;
    }

    public long idHigh(final int index) {
        return high[index];
    }

    public long idLow(final int index) {
        return low[index];
    }

    public long sessionGeneration(final int index) {
        return sessionGenerations[index];
    }

    public OrderCommandType type(final int index) {
        return types[index];
    }

    public boolean active(final int index) {
        return active[index];
    }

    public void remove(final int index) {
        if (index < 0 || index >= active.length || !active[index]) return;
        active[index] = false;
        types[index] = null;
        size--;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return active.length;
    }
}
