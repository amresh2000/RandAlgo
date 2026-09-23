package com.penguinsecure.basis.app.observability;

/** Caller-owned coherent metric snapshot. */
public final class MutableRuntimeMetricsSnapshot {
    private long version,
            coreProgress,
            eventLoopProgress,
            exposure,
            archiveLag,
            disk,
            decisions,
            rejects,
            unknown,
            exporterFailures,
            marketDataToWriteP99,
            marketDataToWriteP999,
            fillToHedgeWriteP99,
            fillToHedgeWriteP999,
            allocatedBytes,
            gcPauseNanos;
    private int occupancy, books, sessions, risk, rates, watchdog, hedgePaths;

    @SuppressWarnings("ParameterNumber")
    MutableRuntimeMetricsSnapshot set(
            final long newVersion,
            final long core,
            final long eventLoop,
            final int queue,
            final int bookMask,
            final int sessionMask,
            final int riskCode,
            final long currentExposure,
            final int rateCode,
            final long lag,
            final long usable,
            final long decisionCount,
            final long rejectCount,
            final long unknownCount,
            final long failures,
            final int watchdogCode,
            final int hedgePathMask,
            final long marketP99,
            final long marketP999,
            final long hedgeP99,
            final long hedgeP999,
            final long allocated,
            final long gcPause) {
        version = newVersion;
        coreProgress = core;
        eventLoopProgress = eventLoop;
        occupancy = queue;
        books = bookMask;
        sessions = sessionMask;
        risk = riskCode;
        exposure = currentExposure;
        rates = rateCode;
        archiveLag = lag;
        disk = usable;
        decisions = decisionCount;
        rejects = rejectCount;
        unknown = unknownCount;
        exporterFailures = failures;
        watchdog = watchdogCode;
        hedgePaths = hedgePathMask;
        marketDataToWriteP99 = marketP99;
        marketDataToWriteP999 = marketP999;
        fillToHedgeWriteP99 = hedgeP99;
        fillToHedgeWriteP999 = hedgeP999;
        allocatedBytes = allocated;
        gcPauseNanos = gcPause;
        return this;
    }

    public long version() {
        return version;
    }

    public long coreProgressNanos() {
        return coreProgress;
    }

    public long eventLoopProgressNanos() {
        return eventLoopProgress;
    }

    public int queueOccupancy() {
        return occupancy;
    }

    public int bookTrustMask() {
        return books;
    }

    public int sessionLiveMask() {
        return sessions;
    }

    public int riskState() {
        return risk;
    }

    public long exposure() {
        return exposure;
    }

    public int rateState() {
        return rates;
    }

    public long archiveLagBytes() {
        return archiveLag;
    }

    public long diskUsableBytes() {
        return disk;
    }

    public long decisions() {
        return decisions;
    }

    public long rejects() {
        return rejects;
    }

    public long unknownOrders() {
        return unknown;
    }

    public long exporterFailures() {
        return exporterFailures;
    }

    public int watchdogState() {
        return watchdog;
    }

    public int hedgePathHealthMask() {
        return hedgePaths;
    }

    public long marketDataToWriteP99Nanos() {
        return marketDataToWriteP99;
    }

    public long marketDataToWriteP999Nanos() {
        return marketDataToWriteP999;
    }

    public long fillToHedgeWriteP99Nanos() {
        return fillToHedgeWriteP99;
    }

    public long fillToHedgeWriteP999Nanos() {
        return fillToHedgeWriteP999;
    }

    public long allocatedBytes() {
        return allocatedBytes;
    }

    public long gcPauseNanos() {
        return gcPauseNanos;
    }
}
