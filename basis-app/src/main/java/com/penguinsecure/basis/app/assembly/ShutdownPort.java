package com.penguinsecure.basis.app.assembly;

/** Ordered shutdown effects. Implementations must make every method idempotent. */
public interface ShutdownPort {
    ShutdownStepStatus drainAndResolve(long deadlineNanos);

    boolean reconcile();

    boolean snapshot();

    boolean flushJournal();

    boolean closeOrderAndPrivateChannels();

    boolean closeMarketDataAndInfrastructure();
}
