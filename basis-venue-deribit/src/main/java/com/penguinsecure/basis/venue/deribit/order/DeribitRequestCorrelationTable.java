package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.core.command.OrderCommandType;

/** Fixed-capacity JSON-RPC request correlation, fenced by socket generation. */
public final class DeribitRequestCorrelationTable {
    private final long[] requestIds;
    private final long[] high;
    private final long[] low;
    private final long[] generations;
    private final OrderCommandType[] types;
    private final boolean[] active;
    private int size;

    public DeribitRequestCorrelationTable(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        requestIds = new long[capacity];
        high = new long[capacity];
        low = new long[capacity];
        generations = new long[capacity];
        types = new OrderCommandType[capacity];
        active = new boolean[capacity];
    }

    public boolean register(
            final long requestId,
            final long idHigh,
            final long idLow,
            final long generation,
            final OrderCommandType type) {
        if (requestId <= 0
                || (idHigh == 0 && idLow == 0)
                || generation <= 0
                || type == null
                || find(requestId) >= 0) return false;
        for (int index = 0; index < active.length; index++) {
            if (!active[index]) {
                requestIds[index] = requestId;
                high[index] = idHigh;
                low[index] = idLow;
                generations[index] = generation;
                types[index] = type;
                active[index] = true;
                size++;
                return true;
            }
        }
        return false;
    }

    public int find(final long requestId) {
        for (int index = 0; index < active.length; index++) {
            if (active[index] && requestIds[index] == requestId) return index;
        }
        return -1;
    }

    public int findIdentity(final long idHigh, final long idLow, final long generation) {
        for (int index = 0; index < active.length; index++) {
            if (active[index]
                    && high[index] == idHigh
                    && low[index] == idLow
                    && generations[index] == generation) return index;
        }
        return -1;
    }

    public void remove(final int index) {
        if (index < 0 || index >= active.length || !active[index]) return;
        active[index] = false;
        types[index] = null;
        size--;
    }

    public boolean active(final int index) {
        return active[index];
    }

    public long requestId(final int index) {
        return requestIds[index];
    }

    public long idHigh(final int index) {
        return high[index];
    }

    public long idLow(final int index) {
        return low[index];
    }

    public long generation(final int index) {
        return generations[index];
    }

    public OrderCommandType type(final int index) {
        return types[index];
    }

    public int capacity() {
        return active.length;
    }

    public int size() {
        return size;
    }
}
