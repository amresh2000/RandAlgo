package com.penguinsecure.basis.sim.capture;

import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Cold replay digest over normalized primitive event content. */
public final class StableMarketDataDigest {
    private final MessageDigest digest;
    private final ByteBuffer scratch =
            ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);

    public StableMarketDataDigest() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void update(final MutableMarketDataEvent event) {
        put(event.venueId());
        put(event.instrumentId());
        put(event.feedProfileId());
        put(event.kind().ordinal());
        put(event.sessionGeneration());
        put(event.receiveEpochNanos());
        put(event.receiveMonoNanos());
        put(event.venueTimestampMillis());
        put(event.matchingEngineTimestampMillis());
        put(event.venueSequence());
        put(event.venueChangeId());
        put(event.venueUpdateId());
        put(event.validationFlags());
        put(event.bidCount());
        put(event.askCount());
        for (int i = 0; i < event.bidCount(); i++) {
            put(event.bidPriceTicks(i));
            put(event.bidQuantityLots(i));
        }
        for (int i = 0; i < event.askCount(); i++) {
            put(event.askPriceTicks(i));
            put(event.askQuantityLots(i));
        }
    }

    public String finishHex() {
        return HexFormat.of().formatHex(digest.digest());
    }

    private void put(final long value) {
        scratch.clear();
        scratch.putLong(value);
        digest.update(scratch.array(), 0, Long.BYTES);
    }
}
