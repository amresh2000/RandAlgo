package com.penguinsecure.basis.journal.replay;

/** Primitive replay counters and terminal status. */
public record ReplayReport(
        ReplayStatus status,
        long applied,
        long exactDuplicates,
        long lastEventSequence,
        long lastFragmentPosition) {}
