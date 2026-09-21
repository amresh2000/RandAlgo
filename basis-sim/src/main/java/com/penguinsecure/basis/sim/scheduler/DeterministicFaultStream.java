package com.penguinsecure.basis.sim.scheduler;

/** Recorded-seed SplitMix64 stream for deterministic fault selection. */
public final class DeterministicFaultStream {
    private final long seed;
    private long state;
    private long draws;

    public DeterministicFaultStream(final long seed) {
        this.seed = seed;
        state = seed;
    }

    public long nextLong() {
        state += 0x9E3779B97F4A7C15L;
        long value = state;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        draws++;
        return value ^ (value >>> 31);
    }

    public int nextInt(final int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    public long seed() {
        return seed;
    }

    public long draws() {
        return draws;
    }
}
