package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.venue.api.json.ByteToken;
import com.penguinsecure.basis.venue.api.json.JsonByteCursor;

/** Bounded execution-ID/content table merging full, fast, and replayed private evidence. */
public final class BybitExecutionDeduplicator {
    public enum Result {
        NEW,
        DUPLICATE,
        CONFLICT,
        CAPACITY_EXHAUSTED
    }

    private static final int MAXIMUM_ID_BYTES = 96;
    private final long[] hashes;
    private final int[] lengths;
    private final byte[] identities;
    private final long[] idHigh;
    private final long[] idLow;
    private final long[] quantities;
    private final long[] prices;
    private final byte[] scratch = new byte[MAXIMUM_ID_BYTES];
    private int size;

    public BybitExecutionDeduplicator(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        hashes = new long[capacity];
        lengths = new int[capacity];
        identities = new byte[capacity * MAXIMUM_ID_BYTES];
        idHigh = new long[capacity];
        idLow = new long[capacity];
        quantities = new long[capacity];
        prices = new long[capacity];
    }

    public Result admit(
            final JsonByteCursor cursor,
            final ByteToken executionId,
            final long localIdHigh,
            final long localIdLow,
            final long quantity,
            final long price) {
        if (cursor == null
                || executionId == null
                || executionId.length() <= 0
                || executionId.length() > MAXIMUM_ID_BYTES
                || quantity <= 0
                || price <= 0) return Result.CONFLICT;
        final long hash = cursor.tokenHash64(executionId);
        for (int index = 0; index < size; index++) {
            if (hashes[index] != hash) continue;
            if (!sameIdentity(cursor, executionId, index)) return Result.CONFLICT;
            return idHigh[index] == localIdHigh
                            && idLow[index] == localIdLow
                            && quantities[index] == quantity
                            && prices[index] == price
                    ? Result.DUPLICATE
                    : Result.CONFLICT;
        }
        if (size == hashes.length) return Result.CAPACITY_EXHAUSTED;
        final int index = size++;
        hashes[index] = hash;
        lengths[index] = executionId.length();
        cursor.copyToken(executionId, identities, index * MAXIMUM_ID_BYTES);
        idHigh[index] = localIdHigh;
        idLow[index] = localIdLow;
        quantities[index] = quantity;
        prices[index] = price;
        return Result.NEW;
    }

    public int size() {
        return size;
    }

    private boolean sameIdentity(
            final JsonByteCursor cursor, final ByteToken token, final int index) {
        if (lengths[index] != token.length()) return false;
        cursor.copyToken(token, scratch, 0);
        final int offset = index * MAXIMUM_ID_BYTES;
        for (int i = 0; i < token.length(); i++) {
            if (identities[offset + i] != scratch[i]) return false;
        }
        return true;
    }
}
