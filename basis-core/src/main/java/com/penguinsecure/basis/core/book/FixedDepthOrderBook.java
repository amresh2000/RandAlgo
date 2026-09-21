package com.penguinsecure.basis.core.book;

import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;

/** Single-writer, fixed-capacity, best-first primitive order book. */
public final class FixedDepthOrderBook {
    private final int venueId;
    private final int instrumentId;
    private final int feedProfileId;
    private final int capacity;
    private final long tickSize;
    private final long lotSize;
    private final long staleAfterNanos;
    private final int warmupUpdatesAfterImage;
    private final BookSequenceMode sequenceMode;
    private final BookSequenceField sequenceField;
    private final long[][] bidPrices;
    private final long[][] bidQuantities;
    private final long[][] askPrices;
    private final long[][] askQuantities;
    private final int[] bidSizes = new int[2];
    private final int[] askSizes = new int[2];
    private final MutableLongResult arithmeticScratch = new MutableLongResult();
    private final MutableLongResult arithmeticResult = new MutableLongResult();

    private int activeIndex;
    private long epoch = 1;
    private long sessionGeneration;
    private long lastSequence;
    private long lastReceiveMonoNanos;
    private long lastVenueTimestampMillis;
    private int remainingWarmupUpdates;
    private BookTrustState trustState = BookTrustState.DISCONNECTED;
    private BookRejectionReason rejectionReason = BookRejectionReason.DISCONNECTED;

    public FixedDepthOrderBook(
            final int venueId,
            final int instrumentId,
            final int feedProfileId,
            final int capacity,
            final long tickSize,
            final long lotSize,
            final long staleAfterNanos,
            final int warmupUpdatesAfterImage,
            final BookSequenceMode sequenceMode,
            final BookSequenceField sequenceField) {
        if (venueId <= 0 || instrumentId <= 0 || feedProfileId <= 0) {
            throw new IllegalArgumentException("route IDs must be positive");
        }
        if (capacity <= 0 || capacity > 200 || tickSize <= 0 || lotSize <= 0) {
            throw new IllegalArgumentException("invalid book bounds");
        }
        if (staleAfterNanos <= 0 || warmupUpdatesAfterImage < 0) {
            throw new IllegalArgumentException("invalid trust bounds");
        }
        if (sequenceMode == null || sequenceField == null) {
            throw new NullPointerException("sequence configuration is required");
        }
        this.venueId = venueId;
        this.instrumentId = instrumentId;
        this.feedProfileId = feedProfileId;
        this.capacity = capacity;
        this.tickSize = tickSize;
        this.lotSize = lotSize;
        this.staleAfterNanos = staleAfterNanos;
        this.warmupUpdatesAfterImage = warmupUpdatesAfterImage;
        this.sequenceMode = sequenceMode;
        this.sequenceField = sequenceField;
        bidPrices = new long[][] {new long[capacity], new long[capacity]};
        bidQuantities = new long[][] {new long[capacity], new long[capacity]};
        askPrices = new long[][] {new long[capacity], new long[capacity]};
        askQuantities = new long[][] {new long[capacity], new long[capacity]};
    }

    public BookMutationStatus apply(final BookUpdateView update) {
        if (update == null) throw new NullPointerException("update is required");
        if (update.venueId() != venueId
                || update.instrumentId() != instrumentId
                || update.feedProfileId() != feedProfileId) {
            return reject(BookRejectionReason.ROUTE_MISMATCH, BookTrustState.SYNCING);
        }
        if (update.validationFlags() != 0) {
            return reject(BookRejectionReason.VALIDATION_FLAGS, BookTrustState.SYNCING);
        }
        if (update.updateType() == null) {
            return reject(BookRejectionReason.UNSUPPORTED_UPDATE, BookTrustState.SYNCING);
        }
        if (update.updateType() == BookUpdateType.RESET) {
            return reject(BookRejectionReason.RESET, BookTrustState.DISCONNECTED);
        }
        if (update.sessionGeneration() <= 0 || update.receiveMonoNanos() <= 0) {
            return reject(BookRejectionReason.SESSION_CHANGED, BookTrustState.SYNCING);
        }

        final boolean sessionChanged = sessionGeneration != update.sessionGeneration();
        if (sessionChanged) {
            invalidate(BookRejectionReason.SESSION_CHANGED, BookTrustState.SYNCING);
            sessionGeneration = update.sessionGeneration();
            lastSequence = 0;
        }

        final boolean image =
                update.updateType() == BookUpdateType.IMAGE
                        || update.updateType() == BookUpdateType.SNAPSHOT;
        if (!image && sessionChanged) {
            return reject(BookRejectionReason.NEED_IMAGE, BookTrustState.SYNCING);
        }
        if (sequenceMode == BookSequenceMode.COMPLETE_IMAGE_MONOTONIC && !image) {
            return reject(BookRejectionReason.UNSUPPORTED_UPDATE, BookTrustState.SYNCING);
        }
        if (!image
                && trustState != BookTrustState.WARMING
                && trustState != BookTrustState.TRUSTED) {
            return reject(BookRejectionReason.NEED_IMAGE, BookTrustState.SYNCING);
        }

        final long sequence = sequence(update);
        if (sequence <= 0 || (lastSequence > 0 && sequence <= lastSequence)) {
            return reject(BookRejectionReason.NON_MONOTONIC_SEQUENCE, BookTrustState.SYNCING);
        }

        final BookRejectionReason validation = validateUpdate(update, image);
        if (validation != BookRejectionReason.NONE) {
            return reject(validation, BookTrustState.SYNCING);
        }

        final int candidate = 1 - activeIndex;
        if (image) {
            writeImage(update, candidate);
        } else {
            copyActive(candidate);
            final BookRejectionReason mutation = applyDeltas(update, candidate);
            if (mutation != BookRejectionReason.NONE) {
                return reject(mutation, BookTrustState.SYNCING);
            }
        }
        if (isCrossed(candidate)) {
            return reject(BookRejectionReason.CROSSED, BookTrustState.SYNCING);
        }
        if (bidSizes[candidate] == 0 || askSizes[candidate] == 0) {
            return reject(BookRejectionReason.EMPTY_BOOK, BookTrustState.SYNCING);
        }

        activeIndex = candidate;
        lastSequence = sequence;
        lastReceiveMonoNanos = update.receiveMonoNanos();
        lastVenueTimestampMillis = update.venueTimestampMillis();
        rejectionReason = BookRejectionReason.NONE;
        if (image && trustState != BookTrustState.TRUSTED) {
            remainingWarmupUpdates = warmupUpdatesAfterImage;
            trustState =
                    remainingWarmupUpdates == 0 ? BookTrustState.TRUSTED : BookTrustState.WARMING;
        } else if (trustState == BookTrustState.WARMING && remainingWarmupUpdates > 0) {
            remainingWarmupUpdates--;
            if (remainingWarmupUpdates == 0) trustState = BookTrustState.TRUSTED;
        }
        return BookMutationStatus.APPLIED;
    }

    public void disconnect() {
        invalidate(BookRejectionReason.DISCONNECTED, BookTrustState.DISCONNECTED);
        sessionGeneration = 0;
        lastSequence = 0;
    }

    public boolean isTrustedAndFresh(final long nowMonoNanos) {
        if (trustState != BookTrustState.TRUSTED) return false;
        if (nowMonoNanos < lastReceiveMonoNanos
                || nowMonoNanos - lastReceiveMonoNanos > staleAfterNanos) {
            invalidate(BookRejectionReason.STALE, BookTrustState.SYNCING);
            return false;
        }
        return true;
    }

    public int capacity() {
        return capacity;
    }

    public int depth(final BookSide side) {
        return side == BookSide.BID ? bidSizes[activeIndex] : askSizes[activeIndex];
    }

    public long priceTicks(final BookSide side, final int level) {
        checkLevel(side, level);
        return side == BookSide.BID ? bidPrices[activeIndex][level] : askPrices[activeIndex][level];
    }

    public long quantityLots(final BookSide side, final int level) {
        checkLevel(side, level);
        return side == BookSide.BID
                ? bidQuantities[activeIndex][level]
                : askQuantities[activeIndex][level];
    }

    public long bestPriceTicks(final BookSide side) {
        return depth(side) == 0 ? 0 : priceTicks(side, 0);
    }

    public NumericStatus quantityThroughPrice(
            final BookSide side, final long limitPriceTicks, final MutableLongResult result) {
        if (side == null || result == null) {
            throw new NullPointerException("side and result are required");
        }
        long total = 0;
        final int size = depth(side);
        for (int level = 0; level < size; level++) {
            final long price = priceTicks(side, level);
            if ((side == BookSide.ASK && price > limitPriceTicks)
                    || (side == BookSide.BID && price < limitPriceTicks)) break;
            final long quantity = quantityLots(side, level);
            if (CheckedDecimalMath.add(total, quantity, result) != NumericStatus.OK) {
                return result.status();
            }
            total = result.value();
        }
        return CheckedDecimalMath.add(0, total, result);
    }

    public ExecutablePriceStatus executablePrice(
            final BookSide restingSide,
            final long requestedQuantityLots,
            final long limitPriceTicks,
            final long nowMonoNanos,
            final MutableExecutablePrice result) {
        if (restingSide == null || result == null) {
            throw new NullPointerException("side and result are required");
        }
        if (requestedQuantityLots <= 0 || limitPriceTicks <= 0) {
            result.set(ExecutablePriceStatus.INVALID_ARGUMENT, requestedQuantityLots, 0, 0, 0, 0);
            return result.status();
        }
        if (!isTrustedAndFresh(nowMonoNanos)) {
            result.set(ExecutablePriceStatus.UNTRUSTED, requestedQuantityLots, 0, 0, 0, 0);
            return result.status();
        }
        long remaining = requestedQuantityLots;
        long executed = 0;
        long product = 0;
        long worst = 0;
        final int size = depth(restingSide);
        for (int level = 0; level < size && remaining > 0; level++) {
            final long price = priceTicks(restingSide, level);
            if ((restingSide == BookSide.ASK && price > limitPriceTicks)
                    || (restingSide == BookSide.BID && price < limitPriceTicks)) break;
            final long available = quantityLots(restingSide, level);
            final long taken = Math.min(remaining, available);
            if (CheckedDecimalMath.multiply(price, taken, arithmeticScratch) != NumericStatus.OK
                    || CheckedDecimalMath.add(product, arithmeticScratch.value(), arithmeticResult)
                            != NumericStatus.OK) {
                result.set(ExecutablePriceStatus.OVERFLOW, requestedQuantityLots, 0, 0, 0, 0);
                return result.status();
            }
            product = arithmeticResult.value();
            executed += taken;
            remaining -= taken;
            worst = price;
        }
        if (executed == 0) {
            result.set(ExecutablePriceStatus.PARTIAL, requestedQuantityLots, 0, 0, 0, 0);
            return result.status();
        }
        final RoundingPolicy rounding =
                restingSide == BookSide.ASK ? RoundingPolicy.CEILING : RoundingPolicy.FLOOR;
        if (CheckedDecimalMath.divide(product, executed, rounding, arithmeticResult)
                != NumericStatus.OK) {
            result.set(ExecutablePriceStatus.OVERFLOW, requestedQuantityLots, 0, 0, 0, 0);
            return result.status();
        }
        final ExecutablePriceStatus status =
                executed == requestedQuantityLots
                        ? ExecutablePriceStatus.OK
                        : ExecutablePriceStatus.PARTIAL;
        result.set(
                status, requestedQuantityLots, executed, product, arithmeticResult.value(), worst);
        return status;
    }

    public BookTrustState trustState() {
        return trustState;
    }

    public BookRejectionReason rejectionReason() {
        return rejectionReason;
    }

    public long epoch() {
        return epoch;
    }

    public long sessionGeneration() {
        return sessionGeneration;
    }

    public long lastSequence() {
        return lastSequence;
    }

    public long lastReceiveMonoNanos() {
        return lastReceiveMonoNanos;
    }

    public long lastVenueTimestampMillis() {
        return lastVenueTimestampMillis;
    }

    private BookRejectionReason validateUpdate(final BookUpdateView update, final boolean image) {
        if (update.bidCount() < 0
                || update.askCount() < 0
                || update.bidCount() > capacity
                || update.askCount() > capacity) return BookRejectionReason.CAPACITY;
        if (image && (update.bidCount() == 0 || update.askCount() == 0)) {
            return BookRejectionReason.EMPTY_BOOK;
        }
        if (!image && update.bidCount() == 0 && update.askCount() == 0) {
            return BookRejectionReason.EMPTY_BOOK;
        }
        BookRejectionReason reason = validateSide(update, BookSide.BID, image);
        if (reason != BookRejectionReason.NONE) return reason;
        reason = validateSide(update, BookSide.ASK, image);
        if (reason != BookRejectionReason.NONE) return reason;
        if (image && update.bidPriceTicks(0) >= update.askPriceTicks(0)) {
            return BookRejectionReason.CROSSED;
        }
        return BookRejectionReason.NONE;
    }

    private BookRejectionReason validateSide(
            final BookUpdateView update, final BookSide side, final boolean image) {
        final int count = side == BookSide.BID ? update.bidCount() : update.askCount();
        long previousPrice = 0;
        for (int index = 0; index < count; index++) {
            final long price = updatePrice(update, side, index);
            final long quantity = updateQuantity(update, side, index);
            if (price <= 0) return BookRejectionReason.INVALID_PRICE;
            if (quantity < 0 || (image && quantity == 0)) {
                return BookRejectionReason.INVALID_QUANTITY;
            }
            if (price % tickSize != 0) return BookRejectionReason.INVALID_TICK;
            if (quantity != 0 && quantity % lotSize != 0) {
                return BookRejectionReason.INVALID_LOT;
            }
            if (image && index > 0) {
                if ((side == BookSide.BID && price >= previousPrice)
                        || (side == BookSide.ASK && price <= previousPrice)) {
                    return price == previousPrice
                            ? BookRejectionReason.DUPLICATE_PRICE
                            : BookRejectionReason.UNORDERED;
                }
            }
            if (!image) {
                for (int duplicate = 0; duplicate < index; duplicate++) {
                    if (price == updatePrice(update, side, duplicate)) {
                        return BookRejectionReason.DUPLICATE_PRICE;
                    }
                }
            }
            previousPrice = price;
        }
        return BookRejectionReason.NONE;
    }

    private void writeImage(final BookUpdateView update, final int candidate) {
        bidSizes[candidate] = update.bidCount();
        askSizes[candidate] = update.askCount();
        for (int index = 0; index < update.bidCount(); index++) {
            bidPrices[candidate][index] = update.bidPriceTicks(index);
            bidQuantities[candidate][index] = update.bidQuantityLots(index);
        }
        for (int index = 0; index < update.askCount(); index++) {
            askPrices[candidate][index] = update.askPriceTicks(index);
            askQuantities[candidate][index] = update.askQuantityLots(index);
        }
    }

    private void copyActive(final int candidate) {
        bidSizes[candidate] = bidSizes[activeIndex];
        askSizes[candidate] = askSizes[activeIndex];
        System.arraycopy(bidPrices[activeIndex], 0, bidPrices[candidate], 0, bidSizes[activeIndex]);
        System.arraycopy(
                bidQuantities[activeIndex], 0, bidQuantities[candidate], 0, bidSizes[activeIndex]);
        System.arraycopy(askPrices[activeIndex], 0, askPrices[candidate], 0, askSizes[activeIndex]);
        System.arraycopy(
                askQuantities[activeIndex], 0, askQuantities[candidate], 0, askSizes[activeIndex]);
    }

    private BookRejectionReason applyDeltas(final BookUpdateView update, final int candidate) {
        for (int index = 0; index < update.bidCount(); index++) {
            final BookRejectionReason reason =
                    mutateSide(
                            BookSide.BID,
                            candidate,
                            update.bidPriceTicks(index),
                            update.bidQuantityLots(index));
            if (reason != BookRejectionReason.NONE) return reason;
        }
        for (int index = 0; index < update.askCount(); index++) {
            final BookRejectionReason reason =
                    mutateSide(
                            BookSide.ASK,
                            candidate,
                            update.askPriceTicks(index),
                            update.askQuantityLots(index));
            if (reason != BookRejectionReason.NONE) return reason;
        }
        return BookRejectionReason.NONE;
    }

    private BookRejectionReason mutateSide(
            final BookSide side, final int candidate, final long price, final long quantity) {
        final long[] prices = side == BookSide.BID ? bidPrices[candidate] : askPrices[candidate];
        final long[] quantities =
                side == BookSide.BID ? bidQuantities[candidate] : askQuantities[candidate];
        int size = side == BookSide.BID ? bidSizes[candidate] : askSizes[candidate];
        int position = 0;
        while (position < size
                && (side == BookSide.BID ? prices[position] > price : prices[position] < price))
            position++;
        final boolean found = position < size && prices[position] == price;
        if (quantity == 0) {
            if (!found) return BookRejectionReason.INVALID_DELETE;
            final int moved = size - position - 1;
            if (moved > 0) {
                System.arraycopy(prices, position + 1, prices, position, moved);
                System.arraycopy(quantities, position + 1, quantities, position, moved);
            }
            size--;
        } else if (found) {
            quantities[position] = quantity;
        } else {
            if (size == capacity) return BookRejectionReason.CAPACITY;
            final int moved = size - position;
            if (moved > 0) {
                System.arraycopy(prices, position, prices, position + 1, moved);
                System.arraycopy(quantities, position, quantities, position + 1, moved);
            }
            prices[position] = price;
            quantities[position] = quantity;
            size++;
        }
        if (side == BookSide.BID) bidSizes[candidate] = size;
        else askSizes[candidate] = size;
        return BookRejectionReason.NONE;
    }

    private boolean isCrossed(final int index) {
        return bidSizes[index] > 0
                && askSizes[index] > 0
                && bidPrices[index][0] >= askPrices[index][0];
    }

    private long sequence(final BookUpdateView update) {
        return switch (sequenceField) {
            case VENUE_SEQUENCE -> update.venueSequence();
            case CHANGE_ID -> update.venueChangeId();
            case UPDATE_ID -> update.venueUpdateId();
        };
    }

    private BookMutationStatus reject(
            final BookRejectionReason reason, final BookTrustState targetState) {
        invalidate(reason, targetState);
        return BookMutationStatus.REJECTED;
    }

    private void invalidate(final BookRejectionReason reason, final BookTrustState targetState) {
        if (trustState == BookTrustState.TRUSTED
                || trustState == BookTrustState.WARMING
                || (reason == BookRejectionReason.SESSION_CHANGED
                        && rejectionReason != BookRejectionReason.SESSION_CHANGED)) epoch++;
        trustState = targetState;
        rejectionReason = reason;
        remainingWarmupUpdates = 0;
    }

    private long updatePrice(final BookUpdateView update, final BookSide side, final int index) {
        return side == BookSide.BID ? update.bidPriceTicks(index) : update.askPriceTicks(index);
    }

    private long updateQuantity(final BookUpdateView update, final BookSide side, final int index) {
        return side == BookSide.BID ? update.bidQuantityLots(index) : update.askQuantityLots(index);
    }

    private void checkLevel(final BookSide side, final int level) {
        if (side == null) throw new NullPointerException("side is required");
        if (level < 0 || level >= depth(side)) throw new IndexOutOfBoundsException(level);
    }
}
