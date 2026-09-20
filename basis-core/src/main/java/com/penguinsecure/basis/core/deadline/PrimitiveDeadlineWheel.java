package com.penguinsecure.basis.core.deadline;

/**
 * Fixed-capacity primitive deadline store with a rotating scan cursor. Both expirations and work
 * inspected per duty cycle are caller-bounded; scheduling and expiry allocate nothing.
 */
public final class PrimitiveDeadlineWheel {
    private final long[] deadlines;
    private final long[] owners;
    private final int[] types;
    private final int[] generations;
    private final int[] freeNext;
    private final boolean[] active;
    private int freeHead;
    private int scanCursor;
    private int activeCount;

    public PrimitiveDeadlineWheel(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        deadlines = new long[capacity];
        owners = new long[capacity];
        types = new int[capacity];
        generations = new int[capacity];
        freeNext = new int[capacity];
        active = new boolean[capacity];
        for (int slot = 0; slot < capacity; slot++) {
            generations[slot] = 1;
            freeNext[slot] = slot + 1;
        }
        freeNext[capacity - 1] = -1;
    }

    public int capacity() {
        return active.length;
    }

    public int activeCount() {
        return activeCount;
    }

    public DeadlineStatus schedule(
            long deadlineNanos, long ownerId, int deadlineType, MutableDeadlineHandle destination) {
        if (destination == null) {
            return DeadlineStatus.INVALID_DEADLINE;
        }
        if (freeHead < 0) {
            return DeadlineStatus.CAPACITY_EXHAUSTED;
        }
        int slot = freeHead;
        freeHead = freeNext[slot];
        deadlines[slot] = deadlineNanos;
        owners[slot] = ownerId;
        types[slot] = deadlineType;
        active[slot] = true;
        activeCount++;
        destination.set(slot, generations[slot]);
        return DeadlineStatus.OK;
    }

    public DeadlineStatus cancel(MutableDeadlineHandle handle) {
        if (!isCurrent(handle)) {
            return DeadlineStatus.STALE_HANDLE;
        }
        release(handle.slot());
        return DeadlineStatus.OK;
    }

    public int expireDue(
            long nowNanos, int maxExpirations, int maxSlotsInspected, DeadlineHandler handler) {
        if (maxExpirations < 0 || maxSlotsInspected < 0 || handler == null) {
            throw new IllegalArgumentException("invalid expiry arguments");
        }
        int expired = 0;
        int inspected = 0;
        while (inspected < maxSlotsInspected && expired < maxExpirations) {
            int slot = scanCursor;
            scanCursor = (scanCursor + 1) % active.length;
            inspected++;
            if (active[slot] && deadlines[slot] - nowNanos <= 0L) {
                long owner = owners[slot];
                int type = types[slot];
                long deadline = deadlines[slot];
                release(slot);
                handler.onDeadline(owner, type, deadline);
                expired++;
            }
        }
        return expired;
    }

    private boolean isCurrent(MutableDeadlineHandle handle) {
        return handle != null
                && handle.slot() >= 0
                && handle.slot() < active.length
                && active[handle.slot()]
                && generations[handle.slot()] == handle.generation();
    }

    private void release(int slot) {
        active[slot] = false;
        activeCount--;
        freeNext[slot] = freeHead;
        freeHead = slot;
        generations[slot]++;
        if (generations[slot] == 0) {
            generations[slot] = 1;
        }
    }
}
