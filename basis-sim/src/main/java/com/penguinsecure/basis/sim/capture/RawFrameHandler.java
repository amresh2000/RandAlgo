package com.penguinsecure.basis.sim.capture;

/** Cold-thread consumer of a capture slot; bytes are valid only for the callback. */
@FunctionalInterface
public interface RawFrameHandler {
    void onFrame(
            int venueId,
            long receiveEpochNanos,
            long receiveMonoNanos,
            byte[] bytes,
            int offset,
            int length);
}
