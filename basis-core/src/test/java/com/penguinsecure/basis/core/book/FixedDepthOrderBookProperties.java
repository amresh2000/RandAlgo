package com.penguinsecure.basis.core.book;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;

@Tag("property")
final class FixedDepthOrderBookProperties {
    @Property(tries = 1_000)
    void validImagesAndDeltasMatchReference(
            @ForAll @IntRange(min = 1_000, max = 100_000) int midpoint,
            @ForAll @IntRange(min = 1, max = 1_000) int bidQuantityUnits,
            @ForAll @IntRange(min = 1, max = 1_000) int askQuantityUnits,
            @ForAll @IntRange(min = 1, max = 1_000) int replacementQuantityUnits) {
        FixedDepthOrderBook primitive = book();
        ReferenceOrderBook reference = new ReferenceOrderBook();
        BookTestUpdate update =
                new BookTestUpdate()
                        .reset(BookUpdateType.SNAPSHOT, 1)
                        .bid(midpoint - 1L, bidQuantityUnits * 10L)
                        .bid(midpoint - 2L, (bidQuantityUnits + 1L) * 10L)
                        .ask(midpoint + 1L, askQuantityUnits * 10L)
                        .ask(midpoint + 2L, (askQuantityUnits + 1L) * 10L);

        assertEquals(BookMutationStatus.APPLIED, primitive.apply(update));
        reference.apply(update);
        assertSameLevels(primitive, reference);

        update.reset(BookUpdateType.DELTA, 2)
                .bid(midpoint - 1L, replacementQuantityUnits * 10L)
                .ask(midpoint + 2L, (replacementQuantityUnits + 1L) * 10L);
        assertEquals(BookMutationStatus.APPLIED, primitive.apply(update));
        reference.apply(update);
        assertSameLevels(primitive, reference);
    }

    @Property(tries = 500)
    void crossedImagesAlwaysRevokeTrust(
            @ForAll @IntRange(min = 1, max = 100_000) int price,
            @ForAll @IntRange(min = 1, max = 1_000) int quantityUnits) {
        FixedDepthOrderBook primitive = book();
        BookTestUpdate crossed =
                new BookTestUpdate()
                        .reset(BookUpdateType.IMAGE, 1)
                        .bid(price, quantityUnits * 10L)
                        .ask(price, quantityUnits * 10L);

        assertEquals(BookMutationStatus.REJECTED, primitive.apply(crossed));
        assertEquals(BookRejectionReason.CROSSED, primitive.rejectionReason());
        assertEquals(BookTrustState.SYNCING, primitive.trustState());
    }

    private static FixedDepthOrderBook book() {
        return new FixedDepthOrderBook(
                1,
                101,
                11,
                8,
                1,
                10,
                1_000_000,
                0,
                BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC,
                BookSequenceField.UPDATE_ID);
    }

    private static void assertSameLevels(
            final FixedDepthOrderBook primitive, final ReferenceOrderBook reference) {
        for (BookSide side : BookSide.values()) {
            assertEquals(reference.depth(side), primitive.depth(side));
            for (int level = 0; level < reference.depth(side); level++) {
                assertEquals(reference.priceTicks(side, level), primitive.priceTicks(side, level));
                assertEquals(
                        reference.quantityLots(side, level), primitive.quantityLots(side, level));
            }
        }
    }
}
