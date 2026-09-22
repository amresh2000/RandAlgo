package com.penguinsecure.basis.sim.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.core.book.BookSequenceField;
import com.penguinsecure.basis.core.book.BookSequenceMode;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataEventKind;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class ConservativePaperFillModelTest {
    @Test
    void fillsOnlyVisibleMarketableDepthAndNeverPassiveQuantity() {
        FixedDepthOrderBook book = book();
        ConservativePaperFillModel model = new ConservativePaperFillModel(1);
        MutablePaperFill fill = new MutablePaperFill();

        model.fill(book, OrderSide.BUY, 100, 100, 101, 1_100, fill);
        assertTrue(fill.proven());
        assertEquals(30, fill.quantity());
        assertEquals(101, fill.priceTicks());

        model.fill(book, OrderSide.BUY, 100, 100, 99, 1_100, fill);
        assertFalse(fill.proven());
    }

    private static FixedDepthOrderBook book() {
        FixedDepthOrderBook book =
                new FixedDepthOrderBook(
                        1,
                        5,
                        1,
                        4,
                        1,
                        1,
                        10_000,
                        0,
                        BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC,
                        BookSequenceField.VENUE_SEQUENCE);
        MutableMarketDataEvent image = new MutableMarketDataEvent(4);
        image.venueId(1);
        image.instrumentId(5);
        image.feedProfileId(1);
        image.kind(MarketDataEventKind.IMAGE);
        image.sessionGeneration(1);
        image.receiveEpochNanos(10_000);
        image.receiveMonoNanos(1_000);
        image.decodeCompleteMonoNanos(1_001);
        image.venueSequence(1);
        image.addBid(99, 50);
        image.addAsk(100, 10);
        image.addAsk(101, 20);
        book.apply(image);
        return book;
    }
}
