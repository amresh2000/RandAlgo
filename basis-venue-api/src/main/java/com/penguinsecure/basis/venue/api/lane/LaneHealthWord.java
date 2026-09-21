package com.penguinsecure.basis.venue.api.lane;

import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Cache-line-isolated health signal which remains writable when its data lane is full. */
public final class LaneHealthWord {
    private static final int CACHE_LINE = 64;
    private static final int STATE_OFFSET = CACHE_LINE;
    private static final int REASON_OFFSET = STATE_OFFSET + Long.BYTES;
    private static final int EPOCH_OFFSET = REASON_OFFSET + Long.BYTES;
    private static final int SESSION_OFFSET = EPOCH_OFFSET + Long.BYTES;
    private static final int SCOPE_OFFSET = SESSION_OFFSET + Long.BYTES;
    private static final int VERSION_OFFSET = SCOPE_OFFSET + Long.BYTES;

    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(CACHE_LINE * 3));

    public LaneHealthWord() {
        publish(LaneHealthState.STOPPED, VenueFailureReason.NONE, 0, 0, 0);
    }

    public void publish(
            final LaneHealthState state,
            final VenueFailureReason reason,
            final long producerEpoch,
            final long sessionGeneration,
            final int affectedScope) {
        if (state == null || reason == null)
            throw new NullPointerException("state and reason are required");
        final long nextVersion = buffer.getLong(VERSION_OFFSET) + 1;
        buffer.putLongRelease(VERSION_OFFSET, nextVersion | 1L);
        buffer.putLong(STATE_OFFSET, state.ordinal());
        buffer.putLong(REASON_OFFSET, reason.ordinal());
        buffer.putLong(EPOCH_OFFSET, producerEpoch);
        buffer.putLong(SESSION_OFFSET, sessionGeneration);
        buffer.putLong(SCOPE_OFFSET, affectedScope);
        buffer.putLongRelease(VERSION_OFFSET, (nextVersion + 1) & ~1L);
    }

    public void read(final LaneHealthSnapshot target) {
        if (target == null) throw new NullPointerException("target is required");
        while (true) {
            final long before = buffer.getLongVolatile(VERSION_OFFSET);
            if ((before & 1L) != 0) continue;
            target.set(
                    LaneHealthState.values()[(int) buffer.getLong(STATE_OFFSET)],
                    VenueFailureReason.values()[(int) buffer.getLong(REASON_OFFSET)],
                    buffer.getLong(EPOCH_OFFSET),
                    buffer.getLong(SESSION_OFFSET),
                    (int) buffer.getLong(SCOPE_OFFSET),
                    before);
            final long after = buffer.getLongVolatile(VERSION_OFFSET);
            if (before == after && (after & 1L) == 0) return;
        }
    }
}
