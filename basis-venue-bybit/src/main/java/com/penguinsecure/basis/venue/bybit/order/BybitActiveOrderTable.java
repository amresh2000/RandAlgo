package com.penguinsecure.basis.venue.bybit.order;

/** Fixed-capacity registry of locally owned Bybit orders awaiting a terminal private fact. */
public final class BybitActiveOrderTable {
    private final long[] high;
    private final long[] low;
    private final long[] quantity;
    private final long[] filled;
    private final boolean[] active;
    private int size;

    public BybitActiveOrderTable(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        high = new long[capacity];
        low = new long[capacity];
        quantity = new long[capacity];
        filled = new long[capacity];
        active = new boolean[capacity];
    }

    public boolean register(final long idHigh, final long idLow, final long orderQuantity) {
        if ((idHigh == 0 && idLow == 0) || orderQuantity <= 0 || find(idHigh, idLow) >= 0)
            return false;
        for (int index = 0; index < active.length; index++) {
            if (!active[index]) {
                high[index] = idHigh;
                low[index] = idLow;
                quantity[index] = orderQuantity;
                filled[index] = 0;
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

    /** Returns false for an unknown order, overflow, or execution beyond the admitted quantity. */
    public boolean addFill(final long idHigh, final long idLow, final long fillQuantity) {
        final int index = find(idHigh, idLow);
        if (index < 0 || fillQuantity <= 0 || filled[index] > Long.MAX_VALUE - fillQuantity)
            return false;
        final long updated = filled[index] + fillQuantity;
        if (updated > quantity[index]) return false;
        filled[index] = updated;
        if (updated == quantity[index]) remove(index);
        return true;
    }

    public boolean remove(final long idHigh, final long idLow) {
        final int index = find(idHigh, idLow);
        if (index < 0) return false;
        remove(index);
        return true;
    }

    public void remove(final int index) {
        if (index < 0 || index >= active.length || !active[index]) return;
        active[index] = false;
        size--;
    }

    public boolean active(final int index) {
        return active[index];
    }

    public long idHigh(final int index) {
        return high[index];
    }

    public long idLow(final int index) {
        return low[index];
    }

    public int capacity() {
        return active.length;
    }

    public int size() {
        return size;
    }
}
