package com.penguinsecure.basis.venue.api.json;

/** Caller-owned unescaped byte range. */
public final class ByteToken {
    private int offset;
    private int length;

    public int offset() {
        return offset;
    }

    public int length() {
        return length;
    }

    void set(final int offset, final int length) {
        this.offset = offset;
        this.length = length;
    }
}
