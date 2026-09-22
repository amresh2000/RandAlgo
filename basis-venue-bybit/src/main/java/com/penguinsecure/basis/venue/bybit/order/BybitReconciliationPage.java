package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.venue.api.order.VenueOrderState;

/** Caller-owned bounded normalized page returned by the HTTP reconciliation transport. */
public final class BybitReconciliationPage {
    private final long[] idHigh;
    private final long[] idLow;
    private final long[] filled;
    private final VenueOrderState[] states;
    private final byte[] nextCursor;
    private int size;
    private int nextCursorLength;

    public BybitReconciliationPage(final int capacity, final int maximumCursorBytes) {
        if (capacity <= 0 || maximumCursorBytes <= 0) {
            throw new IllegalArgumentException("invalid reconciliation page bounds");
        }
        idHigh = new long[capacity];
        idLow = new long[capacity];
        filled = new long[capacity];
        states = new VenueOrderState[capacity];
        nextCursor = new byte[maximumCursorBytes];
    }

    public void reset() {
        size = 0;
        nextCursorLength = 0;
    }

    public boolean add(
            final long high,
            final long low,
            final long authoritativeFilled,
            final VenueOrderState state) {
        if (size == idHigh.length
                || (high == 0 && low == 0)
                || authoritativeFilled < 0
                || state == null) return false;
        idHigh[size] = high;
        idLow[size] = low;
        filled[size] = authoritativeFilled;
        states[size] = state;
        size++;
        return true;
    }

    public boolean nextCursor(final byte[] source, final int offset, final int length) {
        if (source == null
                || offset < 0
                || length < 0
                || source.length - offset < length
                || length > nextCursor.length) return false;
        System.arraycopy(source, offset, nextCursor, 0, length);
        nextCursorLength = length;
        return true;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return idHigh.length;
    }

    public long idHigh(final int index) {
        return idHigh[index];
    }

    public long idLow(final int index) {
        return idLow[index];
    }

    public long filled(final int index) {
        return filled[index];
    }

    public VenueOrderState state(final int index) {
        return states[index];
    }

    public byte[] nextCursorBytes() {
        return nextCursor;
    }

    public int nextCursorLength() {
        return nextCursorLength;
    }
}
