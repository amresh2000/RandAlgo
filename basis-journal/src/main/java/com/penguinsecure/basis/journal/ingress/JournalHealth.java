package com.penguinsecure.basis.journal.ingress;

/** Mutable single-writer health counters exposed as primitive reads. */
public final class JournalHealth {
    private volatile JournalHealthState state = JournalHealthState.HEALTHY;
    private volatile int ingressOccupancyBytes;
    private volatile int criticalReserveRemainingBytes;
    private volatile long failedCriticalOffers;
    private volatile long failedImportantOffers;
    private volatile long droppedLossyOffers;
    private volatile long publicationResult;
    private volatile long archiveLagBytes;
    private volatile long lastProgressNanos;
    private volatile long diskUsableBytes;
    private volatile DiskWatermarkState diskWatermarkState = DiskWatermarkState.HEALTHY;

    void admitted(final int occupancy, final int reserveRemaining, final long nowNanos) {
        ingressOccupancyBytes = occupancy;
        criticalReserveRemainingBytes = reserveRemaining;
        lastProgressNanos = nowNanos;
    }

    void failed(final JournalEventClass eventClass, final boolean globalFault) {
        if (eventClass == JournalEventClass.CRITICAL) failedCriticalOffers++;
        else if (eventClass == JournalEventClass.IMPORTANT) failedImportantOffers++;
        else droppedLossyOffers++;
        if (globalFault) state = JournalHealthState.GLOBAL_FAULT;
        else if (state == JournalHealthState.HEALTHY)
            state = JournalHealthState.INITIATION_DISARMED;
    }

    public void publication(final long result, final long lagBytes, final long nowNanos) {
        publicationResult = result;
        archiveLagBytes = lagBytes;
        if (result >= 0) lastProgressNanos = nowNanos;
    }

    public void disarmInitiation() {
        if (state == JournalHealthState.HEALTHY) state = JournalHealthState.INITIATION_DISARMED;
    }

    public void globalFault() {
        state = JournalHealthState.GLOBAL_FAULT;
    }

    public void storage(
            final long usableBytes, final long highWaterBytes, final long criticalWaterBytes) {
        if (usableBytes < 0 || criticalWaterBytes < 0 || highWaterBytes < criticalWaterBytes) {
            globalFault();
            return;
        }
        diskUsableBytes = usableBytes;
        if (usableBytes <= criticalWaterBytes) {
            diskWatermarkState = DiskWatermarkState.CRITICAL;
            globalFault();
        } else if (usableBytes <= highWaterBytes) {
            diskWatermarkState = DiskWatermarkState.HIGH;
            disarmInitiation();
        } else {
            diskWatermarkState = DiskWatermarkState.HEALTHY;
        }
    }

    public JournalHealthState state() {
        return state;
    }

    public int ingressOccupancyBytes() {
        return ingressOccupancyBytes;
    }

    public int criticalReserveRemainingBytes() {
        return criticalReserveRemainingBytes;
    }

    public long failedCriticalOffers() {
        return failedCriticalOffers;
    }

    public long failedImportantOffers() {
        return failedImportantOffers;
    }

    public long droppedLossyOffers() {
        return droppedLossyOffers;
    }

    public long publicationResult() {
        return publicationResult;
    }

    public long archiveLagBytes() {
        return archiveLagBytes;
    }

    public long lastProgressNanos() {
        return lastProgressNanos;
    }

    public long diskUsableBytes() {
        return diskUsableBytes;
    }

    public DiskWatermarkState diskWatermarkState() {
        return diskWatermarkState;
    }
}
