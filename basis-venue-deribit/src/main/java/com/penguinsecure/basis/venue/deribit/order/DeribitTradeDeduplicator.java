package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;

/** Currency-scoped fixed-capacity trade identity/content table. */
public final class DeribitTradeDeduplicator {
    public enum Result {
        NEW,
        DUPLICATE,
        CONFLICT,
        CAPACITY_EXHAUSTED
    }

    private static final int ID_BYTES = 128;
    private final long[] hashes, high, low, quantities, prices;
    private final short[] lengths;
    private final byte[] ids, scratch = new byte[ID_BYTES];
    private int size;

    public DeribitTradeDeduplicator(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        hashes = new long[capacity];
        high = new long[capacity];
        low = new long[capacity];
        quantities = new long[capacity];
        prices = new long[capacity];
        lengths = new short[capacity];
        ids = new byte[Math.multiplyExact(capacity, ID_BYTES)];
    }

    public Result admit(
            final JsonByteCursor cursor,
            final ByteToken id,
            final long idHigh,
            final long idLow,
            final long quantity,
            final long price) {
        if (id.length() <= 0
                || id.length() > ID_BYTES
                || quantity <= 0
                || price <= 0
                || !cursor.copyToken(id, scratch, 0)) return Result.CONFLICT;
        final long hash = cursor.tokenHash64(id);
        for (int index = 0; index < size; index++)
            if (hashes[index] == hash) {
                if (lengths[index] != id.length()) return Result.CONFLICT;
                final int offset = index * ID_BYTES;
                for (int i = 0; i < id.length(); i++)
                    if (ids[offset + i] != scratch[i]) return Result.CONFLICT;
                return high[index] == idHigh
                                && low[index] == idLow
                                && quantities[index] == quantity
                                && prices[index] == price
                        ? Result.DUPLICATE
                        : Result.CONFLICT;
            }
        if (size == hashes.length) return Result.CAPACITY_EXHAUSTED;
        final int index = size++;
        hashes[index] = hash;
        lengths[index] = (short) id.length();
        System.arraycopy(scratch, 0, ids, index * ID_BYTES, id.length());
        high[index] = idHigh;
        low[index] = idLow;
        quantities[index] = quantity;
        prices[index] = price;
        return Result.NEW;
    }

    public int size() {
        return size;
    }
}
