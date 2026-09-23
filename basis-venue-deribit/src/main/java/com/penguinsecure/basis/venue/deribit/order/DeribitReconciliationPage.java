package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.venue.api.order.VenueOrderState;

/** Caller-owned bounded normalized Deribit reconciliation page. */
public final class DeribitReconciliationPage {
    private final long[] high, low, filled;
    private final VenueOrderState[] states;
    private int size, nextOffset;
    private boolean hasMore;

    public DeribitReconciliationPage(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        high = new long[capacity];
        low = new long[capacity];
        filled = new long[capacity];
        states = new VenueOrderState[capacity];
    }

    public void reset() {
        size = 0;
        nextOffset = 0;
        hasMore = false;
    }

    public boolean add(
            final long idHigh,
            final long idLow,
            final long authoritativeFilled,
            final VenueOrderState state) {
        if (size == high.length
                || (idHigh == 0 && idLow == 0)
                || authoritativeFilled < 0
                || state == null) return false;
        high[size] = idHigh;
        low[size] = idLow;
        filled[size] = authoritativeFilled;
        states[size] = state;
        size++;
        return true;
    }

    public void continuation(final int offset, final boolean more) {
        nextOffset = offset;
        hasMore = more;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return high.length;
    }

    public long idHigh(final int index) {
        return high[index];
    }

    public long idLow(final int index) {
        return low[index];
    }

    public long filled(final int index) {
        return filled[index];
    }

    public VenueOrderState state(final int index) {
        return states[index];
    }

    public int nextOffset() {
        return nextOffset;
    }

    public boolean hasMore() {
        return hasMore;
    }
}
