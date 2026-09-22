package com.penguinsecure.basis.core.deadline;

public enum DeadlineStatus {
    OK(0),
    CAPACITY_EXHAUSTED(1),
    INVALID_DEADLINE(2),
    STALE_HANDLE(3);

    private final int code;

    DeadlineStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
