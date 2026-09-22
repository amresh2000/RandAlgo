package com.penguinsecure.basis.app.marketdata;

import com.penguinsecure.basis.venue.api.json.ReadableBytes;
import com.penguinsecure.basis.venue.api.marketdata.RawFrameSink;

/** Counts frames and bytes without retaining public wire payloads. */
final class CountingRawFrameSink implements RawFrameSink {
    private volatile long frames;
    private volatile long bytes;

    @Override
    public boolean offer(
            final int venueId,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final ReadableBytes input,
            final int offset,
            final int length) {
        frames++;
        bytes += length;
        return true;
    }

    long frames() {
        return frames;
    }

    long bytes() {
        return bytes;
    }
}
