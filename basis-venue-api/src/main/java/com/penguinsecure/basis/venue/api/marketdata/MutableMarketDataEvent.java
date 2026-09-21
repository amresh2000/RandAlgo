package com.penguinsecure.basis.venue.api.marketdata;

import com.penguinsecure.basis.core.book.BookUpdateType;
import com.penguinsecure.basis.core.book.BookUpdateView;

/** Caller-owned bounded scratch event. Arrays are allocated once at construction. */
public final class MutableMarketDataEvent implements BookUpdateView {
    public static final int ABSOLUTE_MAX_DEPTH = 200;

    private final long[] bidPriceTicks;
    private final long[] bidQuantityLots;
    private final long[] askPriceTicks;
    private final long[] askQuantityLots;
    private int venueId;
    private int instrumentId;
    private int feedProfileId;
    private MarketDataEventKind kind;
    private long sessionGeneration;
    private long receiveEpochNanos;
    private long receiveMonoNanos;
    private long decodeCompleteMonoNanos;
    private long ringCommitMonoNanos;
    private long venueTimestampMillis;
    private long matchingEngineTimestampMillis;
    private long venueSequence;
    private long venueChangeId;
    private long venueUpdateId;
    private int validationFlags;
    private int bidCount;
    private int askCount;

    public MutableMarketDataEvent(final int maximumDepth) {
        if (maximumDepth <= 0 || maximumDepth > ABSOLUTE_MAX_DEPTH) {
            throw new IllegalArgumentException("invalid maximumDepth");
        }
        bidPriceTicks = new long[maximumDepth];
        bidQuantityLots = new long[maximumDepth];
        askPriceTicks = new long[maximumDepth];
        askQuantityLots = new long[maximumDepth];
        reset();
    }

    public void reset() {
        venueId = 0;
        instrumentId = 0;
        feedProfileId = 0;
        kind = null;
        sessionGeneration = 0;
        receiveEpochNanos = 0;
        receiveMonoNanos = 0;
        decodeCompleteMonoNanos = 0;
        ringCommitMonoNanos = 0;
        venueTimestampMillis = 0;
        matchingEngineTimestampMillis = 0;
        venueSequence = 0;
        venueChangeId = 0;
        venueUpdateId = 0;
        validationFlags = 0;
        bidCount = 0;
        askCount = 0;
    }

    public int maximumDepth() {
        return bidPriceTicks.length;
    }

    public int venueId() {
        return venueId;
    }

    public void venueId(final int value) {
        venueId = value;
    }

    public int instrumentId() {
        return instrumentId;
    }

    public void instrumentId(final int value) {
        instrumentId = value;
    }

    public int feedProfileId() {
        return feedProfileId;
    }

    public void feedProfileId(final int value) {
        feedProfileId = value;
    }

    public MarketDataEventKind kind() {
        return kind;
    }

    @Override
    public BookUpdateType updateType() {
        if (kind == null) return null;
        return switch (kind) {
            case IMAGE -> BookUpdateType.IMAGE;
            case SNAPSHOT -> BookUpdateType.SNAPSHOT;
            case DELTA -> BookUpdateType.DELTA;
            case RESET -> BookUpdateType.RESET;
        };
    }

    public void kind(final MarketDataEventKind value) {
        kind = value;
    }

    public long sessionGeneration() {
        return sessionGeneration;
    }

    public void sessionGeneration(final long value) {
        sessionGeneration = value;
    }

    public long receiveEpochNanos() {
        return receiveEpochNanos;
    }

    public void receiveEpochNanos(final long value) {
        receiveEpochNanos = value;
    }

    public long receiveMonoNanos() {
        return receiveMonoNanos;
    }

    public void receiveMonoNanos(final long value) {
        receiveMonoNanos = value;
    }

    public long decodeCompleteMonoNanos() {
        return decodeCompleteMonoNanos;
    }

    public void decodeCompleteMonoNanos(final long value) {
        decodeCompleteMonoNanos = value;
    }

    public long ringCommitMonoNanos() {
        return ringCommitMonoNanos;
    }

    public void ringCommitMonoNanos(final long value) {
        ringCommitMonoNanos = value;
    }

    public long venueTimestampMillis() {
        return venueTimestampMillis;
    }

    public void venueTimestampMillis(final long value) {
        venueTimestampMillis = value;
    }

    public long matchingEngineTimestampMillis() {
        return matchingEngineTimestampMillis;
    }

    public void matchingEngineTimestampMillis(final long value) {
        matchingEngineTimestampMillis = value;
    }

    public long venueSequence() {
        return venueSequence;
    }

    public void venueSequence(final long value) {
        venueSequence = value;
    }

    public long venueChangeId() {
        return venueChangeId;
    }

    public void venueChangeId(final long value) {
        venueChangeId = value;
    }

    public long venueUpdateId() {
        return venueUpdateId;
    }

    public void venueUpdateId(final long value) {
        venueUpdateId = value;
    }

    public int validationFlags() {
        return validationFlags;
    }

    public void validationFlags(final int value) {
        validationFlags = value;
    }

    public int bidCount() {
        return bidCount;
    }

    public int askCount() {
        return askCount;
    }

    public long bidPriceTicks(final int index) {
        return bidPriceTicks[index];
    }

    public long bidQuantityLots(final int index) {
        return bidQuantityLots[index];
    }

    public long askPriceTicks(final int index) {
        return askPriceTicks[index];
    }

    public long askQuantityLots(final int index) {
        return askQuantityLots[index];
    }

    public boolean addBid(final long priceTicks, final long quantityLots) {
        if (bidCount == bidPriceTicks.length) return false;
        bidPriceTicks[bidCount] = priceTicks;
        bidQuantityLots[bidCount++] = quantityLots;
        return true;
    }

    public boolean addAsk(final long priceTicks, final long quantityLots) {
        if (askCount == askPriceTicks.length) return false;
        askPriceTicks[askCount] = priceTicks;
        askQuantityLots[askCount++] = quantityLots;
        return true;
    }

    public void copyFrom(final MutableMarketDataEvent source) {
        if (source.bidCount > maximumDepth() || source.askCount > maximumDepth()) {
            throw new IllegalArgumentException("source exceeds destination depth");
        }
        reset();
        venueId = source.venueId;
        instrumentId = source.instrumentId;
        feedProfileId = source.feedProfileId;
        kind = source.kind;
        sessionGeneration = source.sessionGeneration;
        receiveEpochNanos = source.receiveEpochNanos;
        receiveMonoNanos = source.receiveMonoNanos;
        decodeCompleteMonoNanos = source.decodeCompleteMonoNanos;
        ringCommitMonoNanos = source.ringCommitMonoNanos;
        venueTimestampMillis = source.venueTimestampMillis;
        matchingEngineTimestampMillis = source.matchingEngineTimestampMillis;
        venueSequence = source.venueSequence;
        venueChangeId = source.venueChangeId;
        venueUpdateId = source.venueUpdateId;
        validationFlags = source.validationFlags;
        bidCount = source.bidCount;
        askCount = source.askCount;
        System.arraycopy(source.bidPriceTicks, 0, bidPriceTicks, 0, bidCount);
        System.arraycopy(source.bidQuantityLots, 0, bidQuantityLots, 0, bidCount);
        System.arraycopy(source.askPriceTicks, 0, askPriceTicks, 0, askCount);
        System.arraycopy(source.askQuantityLots, 0, askQuantityLots, 0, askCount);
    }

    public boolean isComplete() {
        return venueId > 0
                && instrumentId > 0
                && feedProfileId > 0
                && kind != null
                && sessionGeneration > 0
                && receiveMonoNanos != 0
                && decodeCompleteMonoNanos != 0;
    }
}
