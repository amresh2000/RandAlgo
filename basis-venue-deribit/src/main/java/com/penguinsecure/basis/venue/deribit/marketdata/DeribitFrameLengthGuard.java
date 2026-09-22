package com.penguinsecure.basis.venue.deribit.marketdata;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.ReferenceCountUtil;

final class DeribitFrameLengthGuard extends ChannelInboundHandlerAdapter {
    private final int maximumFrameBytes;

    DeribitFrameLengthGuard(final int maximumFrameBytes) {
        this.maximumFrameBytes = maximumFrameBytes;
    }

    @Override
    public void channelRead(final ChannelHandlerContext context, final Object message) {
        if (message instanceof WebSocketFrame frame
                && frame.content().readableBytes() > maximumFrameBytes) {
            ReferenceCountUtil.release(message);
            context.fireExceptionCaught(
                    new TooLongFrameException("WebSocket fragment exceeds configured maximum"));
            context.close();
            return;
        }
        context.fireChannelRead(message);
    }
}
