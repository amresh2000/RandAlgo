package com.penguinsecure.basis.core.deadline;

@FunctionalInterface
public interface DeadlineHandler {
    void onDeadline(long ownerId, int deadlineType, long deadlineNanos);
}
