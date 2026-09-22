package com.penguinsecure.basis.benchmarks;

import com.penguinsecure.basis.core.command.OrderCommandHandler;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.OrderUrgency;
import com.penguinsecure.basis.core.command.PriorityOrderCommandLane;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/** Steady-state urgent publication-to-consumption benchmark. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class PriorityOrderCommandLaneBenchmark {
    @State(Scope.Thread)
    public static class LaneState {
        private final PriorityOrderCommandLane lane = new PriorityOrderCommandLane(64, 64);
        private final OrderCommandHandler handler = command -> consume(command.quantity());
        private long sequence;
    }

    @Benchmark
    public int urgentRoundTrip(final LaneState state) {
        final long sequence = ++state.sequence;
        state.lane.tryPublish(
                OrderCommandType.SUBMIT,
                OrderUrgency.URGENT,
                1,
                sequence,
                1,
                2,
                OrderSide.SELL,
                10,
                100,
                sequence);
        return state.lane.drainPrioritized(state.handler, 1);
    }

    private static void consume(final long value) {
        if (value == Long.MIN_VALUE) throw new AssertionError("unreachable");
    }
}
