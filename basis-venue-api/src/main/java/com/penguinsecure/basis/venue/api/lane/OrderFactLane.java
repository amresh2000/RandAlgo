package com.penguinsecure.basis.venue.api.lane;

import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.fact.MutableOrderFact;
import com.penguinsecure.basis.core.oems.fact.OrderFactHandler;
import com.penguinsecure.basis.core.oems.fact.OrderFactProvenance;
import com.penguinsecure.basis.core.oems.fact.OrderFactType;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

/** Fixed-layout, non-blocking SPSC ingress for normalized private/order facts. */
public final class OrderFactLane {
    private static final int MESSAGE_TYPE_ID = 2;
    private static final int RECORD_BYTES = 112;
    private static final ChildOrderState[] CHILD_ORDER_STATES = ChildOrderState.values();

    private final OneToOneRingBuffer ring;
    private final LaneHealthWord healthWord;
    private final MonotonicClock clock;
    private final long producerEpoch;
    private final MutableOrderFact readFact = new MutableOrderFact();
    private final MessageHandler readHandler = this::onMessage;
    private OrderFactHandler activeHandler;
    private long failedClaims;
    private long oldestReceiveMonoNanos;

    public OrderFactLane(
            final int capacityBytes,
            final LaneHealthWord healthWord,
            final MonotonicClock clock,
            final long producerEpoch) {
        if (Integer.bitCount(capacityBytes) != 1 || capacityBytes / 8 < RECORD_BYTES) {
            throw new IllegalArgumentException(
                    "capacityBytes must be a sufficiently large power of two");
        }
        if (healthWord == null || clock == null) {
            throw new NullPointerException("healthWord and clock are required");
        }
        ring =
                new OneToOneRingBuffer(
                        new UnsafeBuffer(
                                ByteBuffer.allocateDirect(
                                        capacityBytes + RingBufferDescriptor.TRAILER_LENGTH)));
        this.healthWord = healthWord;
        this.clock = clock;
        this.producerEpoch = producerEpoch;
    }

    public boolean publish(final MutableOrderFact fact) {
        if (fact == null) throw new NullPointerException("fact is required");
        if (!fact.isComplete()) return false;
        final int index = ring.tryClaim(MESSAGE_TYPE_ID, RECORD_BYTES);
        if (index < 0) {
            failedClaims++;
            healthWord.publish(
                    LaneHealthState.OVERFLOW,
                    VenueFailureReason.RING_OVERFLOW,
                    producerEpoch,
                    fact.sessionGeneration(),
                    fact.venueId());
            return false;
        }
        if (oldestReceiveMonoNanos == 0) oldestReceiveMonoNanos = fact.receiveMonoNanos();
        encode(ring.buffer(), index, fact, clock.nanoTime());
        ring.commit(index);
        return true;
    }

    public int drain(final OrderFactHandler handler, final int messageLimit) {
        if (handler == null) throw new NullPointerException("handler is required");
        if (messageLimit < 0)
            throw new IllegalArgumentException("messageLimit must be non-negative");
        activeHandler = handler;
        final int read;
        try {
            read = ring.read(readHandler, messageLimit);
        } finally {
            activeHandler = null;
        }
        if (ring.size() == 0) oldestReceiveMonoNanos = 0;
        return read;
    }

    public LaneHealthWord healthWord() {
        return healthWord;
    }

    public int sizeBytes() {
        return ring.size();
    }

    public int capacityBytes() {
        return ring.capacity();
    }

    public long failedClaims() {
        return failedClaims;
    }

    public long oldestAgeNanos(final long nowMonoNanos) {
        if (oldestReceiveMonoNanos == 0) return 0;
        return nowMonoNanos >= oldestReceiveMonoNanos
                ? nowMonoNanos - oldestReceiveMonoNanos
                : Long.MAX_VALUE;
    }

    private void onMessage(
            final int messageTypeId,
            final MutableDirectBuffer buffer,
            final int offset,
            final int length) {
        if (messageTypeId != MESSAGE_TYPE_ID || length != RECORD_BYTES) {
            throw new IllegalStateException("invalid order-fact record");
        }
        decode(buffer, offset, readFact);
        activeHandler.onOrderFact(readFact);
    }

    private static void encode(
            final MutableDirectBuffer buffer,
            final int offset,
            final MutableOrderFact fact,
            final long commitMonoNanos) {
        buffer.putInt(offset, fact.type().code());
        buffer.putInt(offset + 4, fact.provenance().code());
        buffer.putLong(offset + 8, fact.localOrderIdHigh());
        buffer.putLong(offset + 16, fact.localOrderIdLow());
        buffer.putInt(offset + 24, fact.venueId());
        buffer.putInt(offset + 28, fact.instrumentId());
        buffer.putLong(offset + 32, fact.sessionGeneration());
        buffer.putLong(offset + 40, fact.receiveEpochNanos());
        buffer.putLong(offset + 48, fact.receiveMonoNanos());
        buffer.putLong(offset + 56, commitMonoNanos);
        buffer.putLong(offset + 64, fact.executionIdentityHash());
        buffer.putLong(offset + 72, fact.fillQuantity());
        buffer.putLong(offset + 80, fact.fillPriceTicks());
        buffer.putLong(offset + 88, fact.authoritativeFilled());
        buffer.putInt(
                offset + 96,
                fact.authoritativeState() == null ? -1 : fact.authoritativeState().ordinal());
        buffer.putInt(offset + 100, fact.reasonCode());
        buffer.putLong(offset + 104, 0);
    }

    private static void decode(
            final MutableDirectBuffer buffer, final int offset, final MutableOrderFact fact) {
        final int stateCode = buffer.getInt(offset + 96);
        final ChildOrderState state = stateCode < 0 ? null : CHILD_ORDER_STATES[stateCode];
        fact.set(
                OrderFactType.fromCode(buffer.getInt(offset)),
                OrderFactProvenance.fromCode(buffer.getInt(offset + 4)),
                buffer.getLong(offset + 8),
                buffer.getLong(offset + 16),
                buffer.getInt(offset + 24),
                buffer.getInt(offset + 28),
                buffer.getLong(offset + 32),
                buffer.getLong(offset + 40),
                buffer.getLong(offset + 48),
                buffer.getLong(offset + 64),
                buffer.getLong(offset + 72),
                buffer.getLong(offset + 80),
                buffer.getLong(offset + 88),
                state,
                buffer.getInt(offset + 100));
    }
}
