package com.penguinsecure.basis.sim.capture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MutableMarketDataEvent;
import com.penguinsecure.basis.venue.bybit.marketdata.BybitOrderBookParser;
import com.penguinsecure.basis.venue.deribit.marketdata.DeribitBoundedBookParser;
import com.sun.management.ThreadMXBean;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
final class MarketDataParserAllocationTest {
    private static final int WARMUP_ITERATIONS = 50_000;
    private static final int MEASURED_ITERATIONS = 100_000;

    @Test
    void parsersAllocateZeroBytesPerMessageAfterWarmup() throws IOException {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        long threadId = Thread.currentThread().threadId();

        ByteBuf bybitFrame =
                Unpooled.wrappedBuffer(
                        resource("/wire/market-data/bybit-orderbook-50-snapshot.json"));
        BybitOrderBookParser bybit = new BybitOrderBookParser(() -> 30);
        MarketDataFeedProfile bybitProfile =
                new MarketDataFeedProfile(
                        1, 1, 101, "BTCUSDT", "orderbook.50.BTCUSDT", 2, 3, 50, 65_536, true);
        MutableMarketDataEvent bybitEvent = new MutableMarketDataEvent(50);
        exerciseBybit(bybit, bybitFrame, bybitProfile, bybitEvent, WARMUP_ITERATIONS);
        long before = bean.getThreadAllocatedBytes(threadId);
        exerciseBybit(bybit, bybitFrame, bybitProfile, bybitEvent, MEASURED_ITERATIONS);
        long bybitAllocated = bean.getThreadAllocatedBytes(threadId) - before;

        ByteBuf deribitFrame =
                Unpooled.wrappedBuffer(resource("/wire/market-data/deribit-bounded-20-image.json"));
        DeribitBoundedBookParser deribit = new DeribitBoundedBookParser(() -> 31);
        MarketDataFeedProfile deribitProfile =
                new MarketDataFeedProfile(
                        2,
                        2,
                        202,
                        "BTC-PERPETUAL",
                        "book.BTC-PERPETUAL.none.20.100ms",
                        2,
                        3,
                        20,
                        65_536,
                        true);
        MutableMarketDataEvent deribitEvent = new MutableMarketDataEvent(20);
        exerciseDeribit(deribit, deribitFrame, deribitProfile, deribitEvent, WARMUP_ITERATIONS);
        before = bean.getThreadAllocatedBytes(threadId);
        exerciseDeribit(deribit, deribitFrame, deribitProfile, deribitEvent, MEASURED_ITERATIONS);
        long deribitAllocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertSubByteSteadyState("Bybit", bybitAllocated);
        assertSubByteSteadyState("Deribit", deribitAllocated);
    }

    private static void exerciseBybit(
            final BybitOrderBookParser parser,
            final ByteBuf frame,
            final MarketDataFeedProfile profile,
            final MutableMarketDataEvent event,
            final int iterations) {
        for (int i = 0; i < iterations; i++) parser.parse(frame, profile, 1, 10, 20, event);
    }

    private static void exerciseDeribit(
            final DeribitBoundedBookParser parser,
            final ByteBuf frame,
            final MarketDataFeedProfile profile,
            final MutableMarketDataEvent event,
            final int iterations) {
        for (int i = 0; i < iterations; i++) parser.parse(frame, profile, 1, 11, 21, event);
    }

    private static byte[] resource(final String name) throws IOException {
        try (InputStream input = MarketDataParserAllocationTest.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("missing resource " + name);
            return input.readAllBytes();
        }
    }

    private static void assertSubByteSteadyState(final String venue, final long allocatedBytes) {
        assertTrue(
                allocatedBytes < MEASURED_ITERATIONS,
                () ->
                        venue
                                + " parser allocated "
                                + allocatedBytes
                                + " bytes across "
                                + MEASURED_ITERATIONS
                                + " messages");
    }
}
