package com.penguinsecure.basis.app.marketdata;

import com.penguinsecure.basis.core.book.BookSequenceField;
import com.penguinsecure.basis.core.book.BookSequenceMode;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.product.ProductFamily;
import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.core.time.system.SystemEpochClock;
import com.penguinsecure.basis.core.time.system.SystemMonotonicClock;
import com.penguinsecure.basis.venue.api.lane.LaneHealthSnapshot;
import com.penguinsecure.basis.venue.api.lane.LaneHealthWord;
import com.penguinsecure.basis.venue.api.lane.MarketDataLane;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataFeedProfile;
import com.penguinsecure.basis.venue.api.marketdata.MarketDataSource;
import com.penguinsecure.basis.venue.api.session.ReconnectPolicy;
import com.penguinsecure.basis.venue.bybit.marketdata.BybitEndpoint;
import com.penguinsecure.basis.venue.bybit.marketdata.BybitMarketDataHandler;
import com.penguinsecure.basis.venue.bybit.marketdata.BybitMarketDataSession;
import com.penguinsecure.basis.venue.bybit.marketdata.BybitNettyWebSocketConnection;
import com.penguinsecure.basis.venue.deribit.marketdata.DeribitMarketDataHandler;
import com.penguinsecure.basis.venue.deribit.marketdata.DeribitMarketDataSession;
import com.penguinsecure.basis.venue.deribit.marketdata.DeribitNettyWebSocketConnection;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Observation-only live public-feed runner. It has no order-entry or credential surface. */
public final class PublicMarketDataRunner {
    private static final URI DERIBIT_ENDPOINT = URI.create("wss://www.deribit.com/ws/api/v2");
    private static final int LANE_CAPACITY_BYTES = 1 << 20;
    private static final int DRAIN_QUOTA = 1_024;
    private static final int MAXIMUM_FRAME_BYTES = 1 << 20;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final long HANDSHAKE_TIMEOUT_MILLIS = 5_000;
    private static final long HEARTBEAT_INTERVAL_NANOS = 10_000_000_000L;
    private static final long ACTIVITY_TIMEOUT_NANOS = 30_000_000_000L;
    private static final long STALE_AFTER_NANOS = 1_000_000_000L;
    private static final ReconnectPolicy RECONNECT_POLICY =
            new ReconnectPolicy(250_000_000L, 10_000_000_000L, 6, 0);

    private PublicMarketDataRunner() {}

    public static void main(final String[] arguments) {
        try {
            run(PublicMarketDataOptions.parse(arguments));
        } catch (Exception exception) {
            System.err.println("market-data runner failed: " + exception.getMessage());
            System.err.println(
                    "usage: java -jar basis-market-data.jar "
                            + "[--venue=bybit|deribit|both] "
                            + "[--duration-seconds=0..86400] [--report-seconds=1..3600]");
            System.exit(1);
        }
    }

    static void run(final PublicMarketDataOptions options) throws Exception {
        final EpochClock epochClock = new SystemEpochClock();
        final MonotonicClock monotonicClock = new SystemMonotonicClock();
        final EventLoopGroup eventLoopGroup =
                new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        final SslContext sslContext = SslContextBuilder.forClient().build();
        final List<RunningVenue> venues = new ArrayList<>(2);
        final AtomicBoolean running = new AtomicBoolean(true);
        final Thread shutdownHook = new Thread(() -> running.set(false), "market-data-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            if (options.venues().includesBybit()) {
                venues.add(createBybit(eventLoopGroup, sslContext, epochClock, monotonicClock));
            }
            if (options.venues().includesDeribit()) {
                venues.add(createDeribit(eventLoopGroup, sslContext, epochClock, monotonicClock));
            }
            System.out.println(
                    "Observation mode only: no credentials, private streams, or order entry are"
                            + " loaded.");
            System.out.println(
                    "venue->receive uses wall-clock timestamps; trust it only on a synchronized"
                            + " host.");
            for (RunningVenue venue : venues) venue.start();
            drive(options, venues, monotonicClock, running);
            for (RunningVenue venue : venues) {
                venue.report();
                if (venue.events() == 0) {
                    throw new IllegalStateException(
                            venue.name() + " produced no normalized events");
                }
                if (!venue.isTrustedWithoutRejections()) {
                    throw new IllegalStateException(
                            venue.name() + " did not maintain a trustworthy order book");
                }
            }
        } finally {
            for (RunningVenue venue : venues) venue.close();
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is already in progress.
            }
        }
    }

    private static void drive(
            final PublicMarketDataOptions options,
            final List<RunningVenue> venues,
            final MonotonicClock clock,
            final AtomicBoolean running) {
        final long start = clock.nanoTime();
        final long durationNanos = options.durationSeconds() * 1_000_000_000L;
        final long deadline =
                options.durationSeconds() == 0 ? Long.MAX_VALUE : start + durationNanos;
        final long reportInterval = options.reportSeconds() * 1_000_000_000L;
        long nextReport = start + reportInterval;
        while (running.get() && clock.nanoTime() < deadline) {
            int work = 0;
            for (RunningVenue venue : venues) work += venue.doWork();
            final long now = clock.nanoTime();
            if (now >= nextReport) {
                for (RunningVenue venue : venues) venue.report();
                do {
                    nextReport += reportInterval;
                } while (nextReport <= now);
            }
            if (work == 0) Thread.onSpinWait();
        }
    }

    private static RunningVenue createBybit(
            final EventLoopGroup eventLoopGroup,
            final SslContext sslContext,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock) {
        final MarketDataFeedProfile profile =
                new MarketDataFeedProfile(
                        1,
                        1,
                        1,
                        "BTCUSD",
                        "orderbook.50.BTCUSD",
                        2,
                        3,
                        50,
                        MAXIMUM_FRAME_BYTES,
                        true);
        final LaneHealthWord health = new LaneHealthWord();
        final CountingRawFrameSink rawFrames = new CountingRawFrameSink();
        final MarketDataLane lane =
                new MarketDataLane(
                        LANE_CAPACITY_BYTES,
                        profile.maximumDepth(),
                        health,
                        monotonicClock,
                        epochClock.epochNanos());
        final CountingMarketDataSink publications = new CountingMarketDataSink(lane);
        final BybitMarketDataSession[] sessionHolder = new BybitMarketDataSession[1];
        final BybitNettyWebSocketConnection connection =
                new BybitNettyWebSocketConnection(
                        BybitEndpoint.forProduct(ProductFamily.INVERSE_PERPETUAL),
                        eventLoopGroup,
                        sslContext,
                        profile.maximumFrameBytes(),
                        CONNECT_TIMEOUT_MILLIS,
                        HANDSHAKE_TIMEOUT_MILLIS,
                        listener ->
                                new BybitMarketDataHandler(
                                        profile,
                                        sessionHolder[0].sessionGeneration(),
                                        epochClock.epochNanos(),
                                        epochClock,
                                        monotonicClock,
                                        publications,
                                        rawFrames,
                                        health,
                                        listener));
        final BybitMarketDataSession session =
                new BybitMarketDataSession(
                        profile.venueInstrument(),
                        profile.maximumDepth(),
                        connection,
                        health,
                        monotonicClock,
                        RECONNECT_POLICY,
                        epochClock.epochNanos(),
                        HEARTBEAT_INTERVAL_NANOS,
                        ACTIVITY_TIMEOUT_NANOS);
        sessionHolder[0] = session;
        connection.bindSessionListener(session);
        final FixedDepthOrderBook book =
                new FixedDepthOrderBook(
                        profile.venueId(),
                        profile.instrumentId(),
                        profile.profileId(),
                        profile.maximumDepth(),
                        10,
                        1_000,
                        STALE_AFTER_NANOS,
                        0,
                        BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC,
                        BookSequenceField.VENUE_SEQUENCE,
                        true);
        final VenueLatencyProbe probe = new VenueLatencyProbe("bybit", book, monotonicClock);
        return new RunningVenue(
                "bybit",
                session,
                session::doWork,
                lane,
                probe,
                health,
                rawFrames,
                publications,
                () -> "");
    }

    private static RunningVenue createDeribit(
            final EventLoopGroup eventLoopGroup,
            final SslContext sslContext,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock) {
        final MarketDataFeedProfile profile =
                new MarketDataFeedProfile(
                        2,
                        2,
                        2,
                        "BTC-PERPETUAL",
                        "book.BTC-PERPETUAL.none.20.100ms",
                        2,
                        3,
                        20,
                        MAXIMUM_FRAME_BYTES,
                        true);
        final LaneHealthWord health = new LaneHealthWord();
        final CountingRawFrameSink rawFrames = new CountingRawFrameSink();
        final MarketDataLane lane =
                new MarketDataLane(
                        LANE_CAPACITY_BYTES,
                        profile.maximumDepth(),
                        health,
                        monotonicClock,
                        epochClock.epochNanos());
        final CountingMarketDataSink publications = new CountingMarketDataSink(lane);
        final DeribitMarketDataSession[] sessionHolder = new DeribitMarketDataSession[1];
        final DeribitMarketDataHandler[] handlerHolder = new DeribitMarketDataHandler[1];
        final DeribitNettyWebSocketConnection connection =
                new DeribitNettyWebSocketConnection(
                        DERIBIT_ENDPOINT,
                        eventLoopGroup,
                        sslContext,
                        profile.maximumFrameBytes(),
                        CONNECT_TIMEOUT_MILLIS,
                        HANDSHAKE_TIMEOUT_MILLIS,
                        listener -> {
                            final DeribitMarketDataHandler handler =
                                    new DeribitMarketDataHandler(
                                            profile,
                                            sessionHolder[0].sessionGeneration(),
                                            epochClock.epochNanos(),
                                            epochClock,
                                            monotonicClock,
                                            publications,
                                            rawFrames,
                                            health,
                                            listener);
                            handlerHolder[0] = handler;
                            return handler;
                        });
        final DeribitMarketDataSession session =
                new DeribitMarketDataSession(
                        profile.venueInstrument(),
                        "100ms",
                        connection,
                        health,
                        monotonicClock,
                        RECONNECT_POLICY,
                        epochClock.epochNanos(),
                        ACTIVITY_TIMEOUT_NANOS);
        sessionHolder[0] = session;
        connection.bindSessionListener(session);
        final FixedDepthOrderBook book =
                new FixedDepthOrderBook(
                        profile.venueId(),
                        profile.instrumentId(),
                        profile.profileId(),
                        profile.maximumDepth(),
                        50,
                        1_000,
                        STALE_AFTER_NANOS,
                        0,
                        BookSequenceMode.COMPLETE_IMAGE_MONOTONIC,
                        BookSequenceField.CHANGE_ID);
        final VenueLatencyProbe probe = new VenueLatencyProbe("deribit", book, monotonicClock);
        return new RunningVenue(
                "deribit",
                session,
                session::doWork,
                lane,
                probe,
                health,
                rawFrames,
                publications,
                () ->
                        handlerHolder[0] == null
                                ? " parseStatus=NOT_CONNECTED"
                                : " parseStatus=" + handlerHolder[0].lastParseStatus());
    }

    private record RunningVenue(
            String name,
            MarketDataSource source,
            IntSupplier sessionWork,
            MarketDataLane lane,
            VenueLatencyProbe probe,
            LaneHealthWord health,
            CountingRawFrameSink rawFrames,
            CountingMarketDataSink publications,
            Supplier<String> diagnostics)
            implements AutoCloseable {
        void start() {
            source.start();
        }

        int doWork() {
            return sessionWork.getAsInt() + lane.drain(probe, DRAIN_QUOTA);
        }

        long events() {
            return probe.events();
        }

        boolean isTrustedWithoutRejections() {
            return probe.isTrustedWithoutRejections();
        }

        void report() {
            final LaneHealthSnapshot snapshot = new LaneHealthSnapshot();
            health.read(snapshot);
            System.out.printf(
                    Locale.ROOT,
                    "%n%s health=%s reason=%s session=%d rawFrames=%d rawBytes=%d"
                            + " publications=%d/%d lastComplete=%s lastEventSession=%d"
                            + " laneBytes=%d%s%n%s%n",
                    name,
                    snapshot.state(),
                    snapshot.reason(),
                    snapshot.sessionGeneration(),
                    rawFrames.frames(),
                    rawFrames.bytes(),
                    publications.published(),
                    publications.attempts(),
                    publications.lastComplete(),
                    publications.lastSessionGeneration(),
                    lane.sizeBytes(),
                    diagnostics.get(),
                    probe.report());
        }

        @Override
        public void close() {
            source.close();
        }
    }
}
