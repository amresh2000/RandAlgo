package com.penguinsecure.basis.core.book;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Slow, allocating test oracle for valid normalized streams. */
final class ReferenceOrderBook {
    private final NavigableMap<Long, Long> bids = new TreeMap<>(Collections.reverseOrder());
    private final NavigableMap<Long, Long> asks = new TreeMap<>();

    void apply(final BookUpdateView update) {
        if (update.updateType() == BookUpdateType.IMAGE
                || update.updateType() == BookUpdateType.SNAPSHOT) {
            bids.clear();
            asks.clear();
        }
        applySide(bids, update, BookSide.BID);
        applySide(asks, update, BookSide.ASK);
    }

    int depth(final BookSide side) {
        return levels(side).size();
    }

    long priceTicks(final BookSide side, final int level) {
        return levels(side).keySet().stream().skip(level).findFirst().orElseThrow();
    }

    long quantityLots(final BookSide side, final int level) {
        return levels(side).values().stream().skip(level).findFirst().orElseThrow();
    }

    private static void applySide(
            final NavigableMap<Long, Long> levels,
            final BookUpdateView update,
            final BookSide side) {
        final int count = side == BookSide.BID ? update.bidCount() : update.askCount();
        for (int index = 0; index < count; index++) {
            final long price =
                    side == BookSide.BID
                            ? update.bidPriceTicks(index)
                            : update.askPriceTicks(index);
            final long quantity =
                    side == BookSide.BID
                            ? update.bidQuantityLots(index)
                            : update.askQuantityLots(index);
            if (quantity == 0) levels.remove(price);
            else levels.put(price, quantity);
        }
    }

    private NavigableMap<Long, Long> levels(final BookSide side) {
        return side == BookSide.BID ? bids : asks;
    }
}
