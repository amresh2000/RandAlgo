package com.penguinsecure.basis.sim.capture;

import com.penguinsecure.basis.venue.api.json.ReadableBytes;
import com.penguinsecure.basis.venue.api.marketdata.RawFrameSink;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Preallocated SPSC copy queue which never blocks a network event loop. */
public final class BoundedRawFrameCapture implements RawFrameSink {
    private static final VarHandle TAIL;
    private static final VarHandle HEAD;

    static {
        try {
            TAIL =
                    MethodHandles.lookup()
                            .findVarHandle(BoundedRawFrameCapture.class, "tail", long.class);
            HEAD =
                    MethodHandles.lookup()
                            .findVarHandle(BoundedRawFrameCapture.class, "head", long.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final byte[][] slots;
    private final int[] lengths;
    private final int[] venueIds;
    private final long[] epochNanos;
    private final long[] monoNanos;
    private final int mask;
    private final int maximumFrameBytes;
    private volatile long tail;
    private volatile long head;
    private volatile long droppedFrames;

    public BoundedRawFrameCapture(final int slotCount, final int maximumFrameBytes) {
        if (Integer.bitCount(slotCount) != 1 || slotCount < 2 || maximumFrameBytes <= 0) {
            throw new IllegalArgumentException(
                    "slot count must be a power of two and frame bound positive");
        }
        this.slots = new byte[slotCount][maximumFrameBytes];
        this.lengths = new int[slotCount];
        this.venueIds = new int[slotCount];
        this.epochNanos = new long[slotCount];
        this.monoNanos = new long[slotCount];
        this.mask = slotCount - 1;
        this.maximumFrameBytes = maximumFrameBytes;
    }

    @Override
    public boolean offer(
            final int venueId,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final ReadableBytes input,
            final int offset,
            final int length) {
        if (input == null) throw new NullPointerException("input is required");
        if (length < 0 || length > maximumFrameBytes || offset < 0) {
            droppedFrames++;
            return false;
        }
        final long currentTail = tail;
        final long currentHead = (long) HEAD.getAcquire(this);
        if (currentTail - currentHead >= slots.length) {
            droppedFrames++;
            return false;
        }
        final int slot = (int) currentTail & mask;
        final byte[] target = slots[slot];
        for (int i = 0; i < length; i++) target[i] = input.getByte(offset + i);
        lengths[slot] = length;
        venueIds[slot] = venueId;
        epochNanos[slot] = receiveEpochNanos;
        monoNanos[slot] = receiveMonoNanos;
        TAIL.setRelease(this, currentTail + 1);
        return true;
    }

    public int drain(final RawFrameHandler handler, final int limit) {
        if (handler == null) throw new NullPointerException("handler is required");
        if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
        long currentHead = head;
        final long availableTail = (long) TAIL.getAcquire(this);
        int count = 0;
        while (currentHead < availableTail && count < limit) {
            final int slot = (int) currentHead & mask;
            handler.onFrame(
                    venueIds[slot],
                    epochNanos[slot],
                    monoNanos[slot],
                    slots[slot],
                    0,
                    lengths[slot]);
            currentHead++;
            count++;
        }
        HEAD.setRelease(this, currentHead);
        return count;
    }

    public long droppedFrames() {
        return droppedFrames;
    }

    public int capacity() {
        return slots.length;
    }
}
