package com.penguinsecure.basis.app.observability;

/** Primitive single-writer metric state copied asynchronously by a cold agent. */
public final class RuntimeMetrics {
    private volatile long version,
            coreProgressNanos,
            eventLoopProgressNanos,
            decisions,
            rejects,
            unknownOrders;
    private volatile long exposure, archiveLagBytes, diskUsableBytes, exporterFailures;
    private volatile long marketDataToWriteP99, marketDataToWriteP999;
    private volatile long fillToHedgeWriteP99, fillToHedgeWriteP999, allocatedBytes, gcPauseNanos;
    private volatile int queueOccupancy, bookTrustMask, sessionLiveMask, riskState, rateState;
    private volatile int watchdogState, hedgePathHealthMask;

    @SuppressWarnings("ParameterNumber")
    public void publish(
            final long coreProgress,
            final long eventLoopProgress,
            final int occupancy,
            final int books,
            final int sessions,
            final int risk,
            final long currentExposure,
            final int rates,
            final long archiveLag,
            final long disk,
            final long decisionCount,
            final long rejectCount,
            final long unknown,
            final int watchdog,
            final int hedgePaths,
            final long marketP99,
            final long marketP999,
            final long hedgeP99,
            final long hedgeP999,
            final long allocated,
            final long gcPause) {
        version++;
        coreProgressNanos = coreProgress;
        eventLoopProgressNanos = eventLoopProgress;
        queueOccupancy = occupancy;
        bookTrustMask = books;
        sessionLiveMask = sessions;
        riskState = risk;
        exposure = currentExposure;
        rateState = rates;
        archiveLagBytes = archiveLag;
        diskUsableBytes = disk;
        decisions = decisionCount;
        rejects = rejectCount;
        unknownOrders = unknown;
        watchdogState = watchdog;
        hedgePathHealthMask = hedgePaths;
        marketDataToWriteP99 = marketP99;
        marketDataToWriteP999 = marketP999;
        fillToHedgeWriteP99 = hedgeP99;
        fillToHedgeWriteP999 = hedgeP999;
        allocatedBytes = allocated;
        gcPauseNanos = gcPause;
        version++;
    }

    public boolean read(final MutableRuntimeMetricsSnapshot target) {
        final long before = version;
        if ((before & 1) != 0) return false;
        target.set(
                before,
                coreProgressNanos,
                eventLoopProgressNanos,
                queueOccupancy,
                bookTrustMask,
                sessionLiveMask,
                riskState,
                exposure,
                rateState,
                archiveLagBytes,
                diskUsableBytes,
                decisions,
                rejects,
                unknownOrders,
                exporterFailures,
                watchdogState,
                hedgePathHealthMask,
                marketDataToWriteP99,
                marketDataToWriteP999,
                fillToHedgeWriteP99,
                fillToHedgeWriteP999,
                allocatedBytes,
                gcPauseNanos);
        return before == version;
    }

    public void exporterFailed() {
        exporterFailures++;
    }
}
