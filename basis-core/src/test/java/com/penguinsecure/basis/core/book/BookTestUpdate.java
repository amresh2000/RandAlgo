package com.penguinsecure.basis.core.book;

final class BookTestUpdate implements BookUpdateView {
    private final long[] bidPrices = new long[16];
    private final long[] bidQuantities = new long[16];
    private final long[] askPrices = new long[16];
    private final long[] askQuantities = new long[16];
    private BookUpdateType type = BookUpdateType.IMAGE;
    private long session = 1;
    private long receive = 1;
    private long sequence = 1;
    private int bidCount;
    private int askCount;

    BookTestUpdate reset(final BookUpdateType newType, final long newSequence) {
        type = newType;
        sequence = newSequence;
        receive = newSequence;
        bidCount = 0;
        askCount = 0;
        return this;
    }

    BookTestUpdate session(final long value) {
        session = value;
        return this;
    }

    BookTestUpdate bid(final long price, final long quantity) {
        bidPrices[bidCount] = price;
        bidQuantities[bidCount++] = quantity;
        return this;
    }

    BookTestUpdate ask(final long price, final long quantity) {
        askPrices[askCount] = price;
        askQuantities[askCount++] = quantity;
        return this;
    }

    @Override
    public int venueId() {
        return 1;
    }

    @Override
    public int instrumentId() {
        return 101;
    }

    @Override
    public int feedProfileId() {
        return 11;
    }

    @Override
    public BookUpdateType updateType() {
        return type;
    }

    @Override
    public long sessionGeneration() {
        return session;
    }

    @Override
    public long receiveMonoNanos() {
        return receive;
    }

    @Override
    public long venueTimestampMillis() {
        return receive;
    }

    @Override
    public long venueSequence() {
        return sequence;
    }

    @Override
    public long venueChangeId() {
        return sequence;
    }

    @Override
    public long venueUpdateId() {
        return sequence;
    }

    @Override
    public int validationFlags() {
        return 0;
    }

    @Override
    public int bidCount() {
        return bidCount;
    }

    @Override
    public long bidPriceTicks(final int index) {
        return bidPrices[index];
    }

    @Override
    public long bidQuantityLots(final int index) {
        return bidQuantities[index];
    }

    @Override
    public int askCount() {
        return askCount;
    }

    @Override
    public long askPriceTicks(final int index) {
        return askPrices[index];
    }

    @Override
    public long askQuantityLots(final int index) {
        return askQuantities[index];
    }
}
