package com.penguinsecure.basis.benchmarks;

import com.penguinsecure.basis.core.book.BookMutationStatus;
import com.penguinsecure.basis.core.book.BookSequenceField;
import com.penguinsecure.basis.core.book.BookSequenceMode;
import com.penguinsecure.basis.core.book.BookSide;
import com.penguinsecure.basis.core.book.BookUpdateType;
import com.penguinsecure.basis.core.book.BookUpdateView;
import com.penguinsecure.basis.core.book.ExecutablePriceStatus;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.book.MutableExecutablePrice;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Phase 4 mutation and executable-depth benchmarks at representative bounded depth. */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FixedDepthOrderBookBenchmark {
    @Benchmark
    public BookMutationStatus imageReplacement(final BookState state) {
        return state.imageBook.apply(state.image.next());
    }

    @Benchmark
    public BookMutationStatus deltaUpdate(final BookState state) {
        return state.deltaBook.apply(state.delta.next());
    }

    @Benchmark
    public long bestAsk(final BookState state) {
        return state.deltaBook.bestPriceTicks(BookSide.ASK);
    }

    @Benchmark
    public ExecutablePriceStatus executableDepth(final BookState state) {
        return state.deltaBook.executablePrice(
                BookSide.ASK, 250, 10_100, state.delta.sequence, state.executable);
    }

    @State(Scope.Thread)
    public static class BookState {
        private final BenchmarkUpdate image = new BenchmarkUpdate(BookUpdateType.IMAGE);
        private final BenchmarkUpdate delta = new BenchmarkUpdate(BookUpdateType.DELTA);
        private final MutableExecutablePrice executable = new MutableExecutablePrice();
        private FixedDepthOrderBook imageBook;
        private FixedDepthOrderBook deltaBook;

        @Setup(Level.Trial)
        public void setup() {
            imageBook = book(BookSequenceMode.COMPLETE_IMAGE_MONOTONIC);
            deltaBook = book(BookSequenceMode.SNAPSHOT_DELTA_MONOTONIC);
            BenchmarkUpdate initial = new BenchmarkUpdate(BookUpdateType.SNAPSHOT);
            deltaBook.apply(initial.next());
            delta.sequence = 1;
        }

        private static FixedDepthOrderBook book(final BookSequenceMode mode) {
            return new FixedDepthOrderBook(
                    1, 101, 11, 64, 1, 1, Long.MAX_VALUE, 0, mode, BookSequenceField.UPDATE_ID);
        }
    }

    private static final class BenchmarkUpdate implements BookUpdateView {
        private final BookUpdateType type;
        private long sequence;

        private BenchmarkUpdate(final BookUpdateType type) {
            this.type = type;
        }

        private BenchmarkUpdate next() {
            sequence++;
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
            return 1;
        }

        @Override
        public long receiveMonoNanos() {
            return sequence;
        }

        @Override
        public long venueTimestampMillis() {
            return sequence;
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
            return type == BookUpdateType.DELTA ? 1 : 20;
        }

        @Override
        public long bidPriceTicks(final int index) {
            return type == BookUpdateType.DELTA ? 9_999 : 10_000L - index;
        }

        @Override
        public long bidQuantityLots(final int index) {
            return 100 + (sequence & 1);
        }

        @Override
        public int askCount() {
            return type == BookUpdateType.DELTA ? 0 : 20;
        }

        @Override
        public long askPriceTicks(final int index) {
            return 10_001L + index;
        }

        @Override
        public long askQuantityLots(final int index) {
            return 100;
        }
    }
}
