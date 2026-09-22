package com.penguinsecure.basis.sim.runner;

/** Optional bridge for scenario kill and health-recovery controls. */
public interface SimulationControlHandler {
    default void onKill(final int scopeId) {}

    default void onArchiveFailure(final int archiveId) {}

    default void onRecoverHedgePath() {}
}
