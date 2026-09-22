package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.venue.api.json.ReadableBytes;
import io.netty.buffer.ByteBuf;

final class BybitOrderByteBufInput implements ReadableBytes {
    private ByteBuf buffer;

    void wrap(final ByteBuf value) {
        buffer = value;
    }

    @Override
    public byte getByte(final int index) {
        return buffer.getByte(index);
    }
}
