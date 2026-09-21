package com.penguinsecure.basis.venue.api.marketdata;

import com.penguinsecure.basis.venue.api.json.ReadableBytes;

/** Non-blocking raw evidence sink. Implementations copy bytes before returning. */
@FunctionalInterface
public interface RawFrameSink {
    RawFrameSink DISCARD =
            (venueId, receiveEpochNanos, receiveMonoNanos, input, offset, length) -> true;

    boolean offer(
            int venueId,
            long receiveEpochNanos,
            long receiveMonoNanos,
            ReadableBytes input,
            int offset,
            int length);
}
