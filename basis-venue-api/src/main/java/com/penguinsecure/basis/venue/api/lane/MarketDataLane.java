package com.penguinsecure.basis.venue.api.lane;

import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataSink;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import com.penguinsecure.basis.venue.api.session.VenueFailureReason;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

/** Fixed-layout, non-blocking SPSC ingress lane. */
public final class MarketDataLane implements MarketDataSink {
    private static final int MESSAGE_TYPE_ID = 1;
    private static final int HEADER_BYTES = 112;
    private static final int LEVEL_BYTES = Long.BYTES * 2;

    private final OneToOneRingBuffer ring;
    private final LaneHealthWord healthWord;
    private final MonotonicClock clock;
    private final long producerEpoch;
    private final int maximumDepth;
    private final MutableMarketDataEvent readEvent;

    public MarketDataLane(
            final int capacityBytes,
            final int maximumDepth,
            final LaneHealthWord healthWord,
            final MonotonicClock clock,
            final long producerEpoch) {
        if (Integer.bitCount(capacityBytes) != 1) {
            throw new IllegalArgumentException("capacityBytes must be a power of two");
        }
        if (maximumDepth <= 0 || maximumDepth > MutableMarketDataEvent.ABSOLUTE_MAX_DEPTH) {
            throw new IllegalArgumentException("invalid maximumDepth");
        }
        if (healthWord == null || clock == null)
            throw new NullPointerException("healthWord and clock are required");
        final int maximumRecord = recordLength(maximumDepth, maximumDepth);
        if (capacityBytes / 8 < maximumRecord) {
            throw new IllegalArgumentException("capacity is too small for maximum record");
        }
        this.ring =
                new OneToOneRingBuffer(
                        new UnsafeBuffer(
                                ByteBuffer.allocateDirect(
                                        capacityBytes + RingBufferDescriptor.TRAILER_LENGTH)));
        this.healthWord = healthWord;
        this.clock = clock;
        this.producerEpoch = producerEpoch;
        this.maximumDepth = maximumDepth;
        this.readEvent = new MutableMarketDataEvent(maximumDepth);
    }

    @Override
    public boolean publish(final MutableMarketDataEvent event) {
        if (event == null) throw new NullPointerException("event is required");
        if (!event.isComplete()
                || event.bidCount() > maximumDepth
                || event.askCount() > maximumDepth) {
            return false;
        }
        final int length = recordLength(event.bidCount(), event.askCount());
        final int index = ring.tryClaim(MESSAGE_TYPE_ID, length);
        if (index < 0) {
            healthWord.publish(
                    LaneHealthState.OVERFLOW,
                    VenueFailureReason.RING_OVERFLOW,
                    producerEpoch,
                    event.sessionGeneration(),
                    event.instrumentId());
            return false;
        }
        final MutableDirectBuffer buffer = ring.buffer();
        encode(buffer, index, event, clock.nanoTime());
        ring.commit(index);
        return true;
    }

    public int drain(final MarketDataEventHandler handler, final int messageLimit) {
        if (handler == null) throw new NullPointerException("handler is required");
        if (messageLimit < 0)
            throw new IllegalArgumentException("messageLimit must be non-negative");
        return ring.read(
                (messageTypeId, buffer, offset, length) -> {
                    if (messageTypeId != MESSAGE_TYPE_ID)
                        throw new IllegalStateException("unknown message type");
                    decode(buffer, offset, length, readEvent);
                    handler.onMarketData(readEvent);
                },
                messageLimit);
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

    private static int recordLength(final int bids, final int asks) {
        return HEADER_BYTES + (bids + asks) * LEVEL_BYTES;
    }

    private static void encode(
            final MutableDirectBuffer buffer,
            final int offset,
            final MutableMarketDataEvent event,
            final long commitNanos) {
        buffer.putInt(offset, event.venueId());
        buffer.putInt(offset + 4, event.instrumentId());
        buffer.putInt(offset + 8, event.feedProfileId());
        buffer.putInt(offset + 12, event.kind().ordinal());
        buffer.putLong(offset + 16, event.sessionGeneration());
        buffer.putLong(offset + 24, event.receiveEpochNanos());
        buffer.putLong(offset + 32, event.receiveMonoNanos());
        buffer.putLong(offset + 40, event.decodeCompleteMonoNanos());
        buffer.putLong(offset + 48, commitNanos);
        buffer.putLong(offset + 56, event.venueTimestampMillis());
        buffer.putLong(offset + 64, event.matchingEngineTimestampMillis());
        buffer.putLong(offset + 72, event.venueSequence());
        buffer.putLong(offset + 80, event.venueChangeId());
        buffer.putLong(offset + 88, event.venueUpdateId());
        buffer.putInt(offset + 96, event.validationFlags());
        buffer.putInt(offset + 100, event.bidCount());
        buffer.putInt(offset + 104, event.askCount());
        buffer.putInt(offset + 108, 0);
        int cursor = offset + HEADER_BYTES;
        for (int i = 0; i < event.bidCount(); i++, cursor += LEVEL_BYTES) {
            buffer.putLong(cursor, event.bidPriceTicks(i));
            buffer.putLong(cursor + Long.BYTES, event.bidQuantityLots(i));
        }
        for (int i = 0; i < event.askCount(); i++, cursor += LEVEL_BYTES) {
            buffer.putLong(cursor, event.askPriceTicks(i));
            buffer.putLong(cursor + Long.BYTES, event.askQuantityLots(i));
        }
    }

    private static void decode(
            final MutableDirectBuffer buffer,
            final int offset,
            final int length,
            final MutableMarketDataEvent event) {
        if (length < HEADER_BYTES) throw new IllegalStateException("truncated market-data record");
        event.reset();
        event.venueId(buffer.getInt(offset));
        event.instrumentId(buffer.getInt(offset + 4));
        event.feedProfileId(buffer.getInt(offset + 8));
        event.kind(MarketDataEventKind.values()[buffer.getInt(offset + 12)]);
        event.sessionGeneration(buffer.getLong(offset + 16));
        event.receiveEpochNanos(buffer.getLong(offset + 24));
        event.receiveMonoNanos(buffer.getLong(offset + 32));
        event.decodeCompleteMonoNanos(buffer.getLong(offset + 40));
        event.ringCommitMonoNanos(buffer.getLong(offset + 48));
        event.venueTimestampMillis(buffer.getLong(offset + 56));
        event.matchingEngineTimestampMillis(buffer.getLong(offset + 64));
        event.venueSequence(buffer.getLong(offset + 72));
        event.venueChangeId(buffer.getLong(offset + 80));
        event.venueUpdateId(buffer.getLong(offset + 88));
        event.validationFlags(buffer.getInt(offset + 96));
        final int bids = buffer.getInt(offset + 100);
        final int asks = buffer.getInt(offset + 104);
        if (bids < 0
                || asks < 0
                || bids > event.maximumDepth()
                || asks > event.maximumDepth()
                || recordLength(bids, asks) != length) {
            throw new IllegalStateException("invalid market-data record bounds");
        }
        int cursor = offset + HEADER_BYTES;
        for (int i = 0; i < bids; i++, cursor += LEVEL_BYTES) {
            event.addBid(buffer.getLong(cursor), buffer.getLong(cursor + Long.BYTES));
        }
        for (int i = 0; i < asks; i++, cursor += LEVEL_BYTES) {
            event.addAsk(buffer.getLong(cursor), buffer.getLong(cursor + Long.BYTES));
        }
    }
}
