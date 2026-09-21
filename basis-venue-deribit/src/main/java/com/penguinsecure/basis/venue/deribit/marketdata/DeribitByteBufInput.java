package com.penguinsecure.basis.venue.deribit.marketdata;

import com.penguinsecure.basis.venue.api.json.ReadableBytes;
import io.netty.buffer.ByteBuf;

final class DeribitByteBufInput implements ReadableBytes {
    private ByteBuf buffer;

    void wrap(final ByteBuf buffer) {
        this.buffer = buffer;
    }

    @Override
    public byte getByte(final int index) {
        return buffer.getByte(index);
    }
}
