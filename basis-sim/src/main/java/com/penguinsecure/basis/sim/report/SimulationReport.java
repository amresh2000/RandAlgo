package com.penguinsecure.basis.sim.report;

import com.penguinsecure.basis.core.oems.OemsStatus;

/** Immutable terminal report for one deterministic run. */
public record SimulationReport(
        String scenario,
        String digest,
        boolean complete,
        long terminalMonoNanos,
        long commands,
        long facts,
        long simulatedFacts,
        long counterfactualFacts,
        long actualFacts,
        long scheduledEvents,
        int checkpoints,
        OemsStatus firstOemsFailure,
        String failureReason) {}
