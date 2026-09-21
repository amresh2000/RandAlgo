package com.penguinsecure.basis.core.book;

/** Read-only primitive view consumed by the single-writer book owner. */
public interface BookUpdateView {
    int venueId();

    int instrumentId();

    int feedProfileId();

    BookUpdateType updateType();

    long sessionGeneration();

    long receiveMonoNanos();

    long venueTimestampMillis();

    long venueSequence();

    long venueChangeId();

    long venueUpdateId();

    int validationFlags();

    int bidCount();

    long bidPriceTicks(int index);

    long bidQuantityLots(int index);

    int askCount();

    long askPriceTicks(int index);

    long askQuantityLots(int index);
}
