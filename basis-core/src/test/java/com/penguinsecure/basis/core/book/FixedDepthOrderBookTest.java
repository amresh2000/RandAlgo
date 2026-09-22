package com.penguinsecure.basis.core.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class FixedDepthOrderBookTest {
    @Test
    void appliesImageWarmsWithDeltaAndComputesExecutablePrice() {
        FixedDepthOrderBook book = book(1, BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC);
        BookTestUpdate update =
                new BookTestUpdate()
                        .reset(BookUpdateType.SNAPSHOT, 10)
                        .bid(100, 20)
                        .bid(99, 30)
                        .ask(101, 10)
                        .ask(102, 20);

        assertEquals(BookMutationStatus.APPLIED, book.apply(update));
        assertEquals(BookTrustState.WARMING, book.trustState());
        assertFalse(book.isTrustedAndFresh(10));

        assertEquals(
                BookMutationStatus.APPLIED,
                book.apply(update.reset(BookUpdateType.DELTA, 11).bid(100, 30)));
        assertEquals(BookTrustState.TRUSTED, book.trustState());
        assertEquals(100, book.bestPriceTicks(BookSide.BID));
        assertEquals(30, book.quantityLots(BookSide.BID, 0));

        MutableExecutablePrice result = new MutableExecutablePrice();
        assertEquals(
                ExecutablePriceStatus.OK, book.executablePrice(BookSide.ASK, 15, 102, 11, result));
        assertEquals(15, result.executedQuantityLots());
        assertEquals(1_520, result.priceQuantityProduct());
        assertEquals(102, result.averagePriceTicks());
        assertEquals(102, result.worstPriceTicks());
        MutableLongResult quantity = new MutableLongResult();
        assertEquals(NumericStatus.OK, book.quantityThroughPrice(BookSide.ASK, 101, quantity));
        assertEquals(10, quantity.value());
    }

    @Test
    void insertsUpdatesAndDeletesWithoutBreakingOrdering() {
        FixedDepthOrderBook book = book(0, BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC);
        BookTestUpdate update =
                new BookTestUpdate()
                        .reset(BookUpdateType.SNAPSHOT, 1)
                        .bid(100, 10)
                        .bid(98, 10)
                        .ask(102, 10)
                        .ask(104, 10);
        book.apply(update);

        assertEquals(
                BookMutationStatus.APPLIED,
                book.apply(
                        update.reset(BookUpdateType.DELTA, 2).bid(99, 20).bid(98, 0).ask(103, 30)));
        assertEquals(2, book.depth(BookSide.BID));
        assertEquals(99, book.priceTicks(BookSide.BID, 1));
        assertEquals(3, book.depth(BookSide.ASK));
        assertEquals(103, book.priceTicks(BookSide.ASK, 1));
    }

    @Test
    void boundedDeltaBookDropsTailAndIgnoresUpdatesOutsideVisibleDepth() {
        FixedDepthOrderBook book =
                new FixedDepthOrderBook(
                        1,
                        101,
                        11,
                        2,
                        1,
                        10,
                        100,
                        0,
                        BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC,
                        BookSequenceField.UPDATE_ID,
                        true);
        BookTestUpdate update =
                new BookTestUpdate()
                        .reset(BookUpdateType.SNAPSHOT, 1)
                        .bid(100, 10)
                        .bid(99, 10)
                        .ask(102, 10)
                        .ask(103, 10);
        assertEquals(BookMutationStatus.APPLIED, book.apply(update));

        assertEquals(
                BookMutationStatus.APPLIED,
                book.apply(update.reset(BookUpdateType.DELTA, 2).bid(101, 20).ask(104, 20)));
        assertEquals(101, book.priceTicks(BookSide.BID, 0));
        assertEquals(100, book.priceTicks(BookSide.BID, 1));
        assertEquals(102, book.priceTicks(BookSide.ASK, 0));
        assertEquals(103, book.priceTicks(BookSide.ASK, 1));
        assertEquals(
                BookMutationStatus.APPLIED,
                book.apply(update.reset(BookUpdateType.DELTA, 3).bid(98, 0).ask(102, 0)));
        assertEquals(1, book.depth(BookSide.ASK));
        assertEquals(103, book.bestPriceTicks(BookSide.ASK));
    }

    @Test
    void malformedOrCrossedStateRevokesTrustAndRequiresNewImage() {
        FixedDepthOrderBook book = book(0, BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC);
        BookTestUpdate update =
                new BookTestUpdate().reset(BookUpdateType.SNAPSHOT, 1).bid(100, 10).ask(101, 10);
        book.apply(update);
        long priorEpoch = book.epoch();

        assertEquals(
                BookMutationStatus.REJECTED,
                book.apply(update.reset(BookUpdateType.DELTA, 2).bid(101, 20)));
        assertEquals(BookRejectionReason.CROSSED, book.rejectionReason());
        assertEquals(BookTrustState.SYNCING, book.trustState());
        assertTrue(book.epoch() > priorEpoch);
        assertEquals(
                BookMutationStatus.REJECTED,
                book.apply(update.reset(BookUpdateType.DELTA, 3).bid(99, 20)));
        assertEquals(BookRejectionReason.NEED_IMAGE, book.rejectionReason());

        assertEquals(
                BookMutationStatus.APPLIED,
                book.apply(update.reset(BookUpdateType.SNAPSHOT, 4).bid(99, 20).ask(102, 20)));
        assertEquals(BookTrustState.TRUSTED, book.trustState());
    }

    @Test
    void sessionChangeStalenessAndImageModeFailClosed() {
        FixedDepthOrderBook book = book(0, BookSequenceMode.COMPLETE_IMAGE_MONOTONIC);
        BookTestUpdate update =
                new BookTestUpdate().reset(BookUpdateType.IMAGE, 1).bid(100, 10).ask(101, 10);
        book.apply(update);
        assertTrue(book.isTrustedAndFresh(50));
        assertFalse(book.isTrustedAndFresh(102));
        assertEquals(BookRejectionReason.STALE, book.rejectionReason());

        assertEquals(
                BookMutationStatus.REJECTED,
                book.apply(update.reset(BookUpdateType.DELTA, 2).session(2).bid(100, 20)));
        assertEquals(BookRejectionReason.NEED_IMAGE, book.rejectionReason());
        assertEquals(BookTrustState.SYNCING, book.trustState());
    }

    @Test
    void rejectsDuplicateUnorderedInvalidGridAndUnknownDelete() {
        FixedDepthOrderBook book = book(0, BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC);
        BookTestUpdate update = new BookTestUpdate();
        assertRejected(
                book,
                update.reset(BookUpdateType.SNAPSHOT, 1).bid(100, 10).bid(100, 20).ask(102, 10),
                BookRejectionReason.DUPLICATE_PRICE);
        assertRejected(
                book,
                update.reset(BookUpdateType.SNAPSHOT, 2).bid(99, 10).bid(100, 20).ask(102, 10),
                BookRejectionReason.UNORDERED);
        assertRejected(
                book,
                update.reset(BookUpdateType.SNAPSHOT, 3).bid(100, 11).ask(102, 10),
                BookRejectionReason.INVALID_LOT);

        book.apply(update.reset(BookUpdateType.SNAPSHOT, 4).bid(100, 10).ask(102, 10));
        assertRejected(
                book,
                update.reset(BookUpdateType.DELTA, 5).bid(99, 0),
                BookRejectionReason.INVALID_DELETE);
    }

    @Test
    void rejectsEmptyDeltaAndOneSidedResultWithoutRepeatedEpochChurn() {
        FixedDepthOrderBook book = book(0, BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC);
        BookTestUpdate update =
                new BookTestUpdate().reset(BookUpdateType.SNAPSHOT, 1).bid(100, 10).ask(102, 10);
        book.apply(update);

        assertRejected(book, update.reset(BookUpdateType.DELTA, 2), BookRejectionReason.EMPTY_BOOK);
        long invalidEpoch = book.epoch();
        assertRejected(
                book,
                update.reset(BookUpdateType.DELTA, 3).ask(102, 0),
                BookRejectionReason.NEED_IMAGE);
        assertEquals(invalidEpoch, book.epoch());

        book.apply(update.reset(BookUpdateType.SNAPSHOT, 4).bid(100, 10).ask(102, 10));
        assertRejected(
                book,
                update.reset(BookUpdateType.DELTA, 5).ask(102, 0),
                BookRejectionReason.EMPTY_BOOK);
    }

    private static FixedDepthOrderBook book(final int warmup, final BookSequenceMode sequenceMode) {
        return new FixedDepthOrderBook(
                1, 101, 11, 8, 1, 10, 100, warmup, sequenceMode, BookSequenceField.UPDATE_ID);
    }

    private static void assertRejected(
            final FixedDepthOrderBook book,
            final BookTestUpdate update,
            final BookRejectionReason reason) {
        assertEquals(BookMutationStatus.REJECTED, book.apply(update));
        assertEquals(reason, book.rejectionReason());
    }
}
