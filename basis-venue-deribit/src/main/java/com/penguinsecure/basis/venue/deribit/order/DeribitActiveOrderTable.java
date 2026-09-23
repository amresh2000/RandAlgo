package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;

/** Fixed-capacity local-order registry with bounded Deribit order-id storage. */
public final class DeribitActiveOrderTable {
    private static final int ORDER_ID_BYTES = 128;
    private final long[] high;
    private final long[] low;
    private final long[] quantity;
    private final long[] filled;
    private final byte[] venueIds;
    private final short[] venueIdLengths;
    private final boolean[] active;
    private final byte[] scratch = new byte[ORDER_ID_BYTES];
    private int size;

    public DeribitActiveOrderTable(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        high = new long[capacity];
        low = new long[capacity];
        quantity = new long[capacity];
        filled = new long[capacity];
        venueIds = new byte[Math.multiplyExact(capacity, ORDER_ID_BYTES)];
        venueIdLengths = new short[capacity];
        active = new boolean[capacity];
    }

    public boolean register(final long idHigh, final long idLow, final long amount) {
        if ((idHigh == 0 && idLow == 0) || amount <= 0 || find(idHigh, idLow) >= 0) return false;
        for (int index = 0; index < active.length; index++)
            if (!active[index]) {
                high[index] = idHigh;
                low[index] = idLow;
                quantity[index] = amount;
                filled[index] = 0;
                venueIdLengths[index] = 0;
                active[index] = true;
                size++;
                return true;
            }
        return false;
    }

    public int find(final long idHigh, final long idLow) {
        for (int index = 0; index < active.length; index++)
            if (active[index] && high[index] == idHigh && low[index] == idLow) return index;
        return -1;
    }

    public int findVenueId(final JsonByteCursor cursor, final ByteToken token) {
        if (token.length() <= 0
                || token.length() > ORDER_ID_BYTES
                || !cursor.copyToken(token, scratch, 0)) return -1;
        for (int index = 0; index < active.length; index++) {
            if (!active[index] || venueIdLengths[index] != token.length()) continue;
            boolean equal = true;
            final int offset = index * ORDER_ID_BYTES;
            for (int byteIndex = 0; byteIndex < token.length(); byteIndex++) {
                if (scratch[byteIndex] != venueIds[offset + byteIndex]) {
                    equal = false;
                    break;
                }
            }
            if (equal) return index;
        }
        return -1;
    }

    public boolean bindVenueId(
            final int index, final JsonByteCursor cursor, final ByteToken token) {
        if (index < 0
                || index >= active.length
                || !active[index]
                || token.length() <= 0
                || token.length() > ORDER_ID_BYTES) return false;
        final int length = venueIdLengths[index];
        final int offset = index * ORDER_ID_BYTES;
        if (length > 0) {
            if (length != token.length()) return false;
            if (!cursor.copyToken(token, scratch, 0)) return false;
            for (int i = 0; i < length; i++) if (scratch[i] != venueIds[offset + i]) return false;
            return true;
        }
        if (!cursor.copyToken(token, venueIds, offset)) return false;
        venueIdLengths[index] = (short) token.length();
        return true;
    }

    public boolean appendVenueId(final int index, final StringBuilder destination) {
        if (index < 0 || index >= active.length || !active[index] || venueIdLengths[index] == 0)
            return false;
        final int offset = index * ORDER_ID_BYTES;
        for (int i = 0; i < venueIdLengths[index]; i++)
            destination.append((char) venueIds[offset + i]);
        return true;
    }

    public boolean addFill(final int index, final long amount) {
        if (index < 0
                || index >= active.length
                || !active[index]
                || amount <= 0
                || filled[index] > Long.MAX_VALUE - amount) return false;
        final long updated = filled[index] + amount;
        if (updated > quantity[index]) return false;
        filled[index] = updated;
        return true;
    }

    public void remove(final int index) {
        if (index < 0 || index >= active.length || !active[index]) return;
        active[index] = false;
        venueIdLengths[index] = 0;
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
