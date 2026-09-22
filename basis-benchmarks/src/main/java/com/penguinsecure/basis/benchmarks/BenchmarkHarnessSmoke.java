package com.penguinsecure.basis.benchmarks;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

/** Verifies that the dedicated performance runner can discover and execute JMH benchmarks. */
public class BenchmarkHarnessSmoke {
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int increment() {
        return 1 + 1;
    }
}
