package com.penguinsecure.basis.journal.ingress;

/** Admission class. Numeric codes are stable and are not Java ordinals. */
public enum JournalEventClass {
    CRITICAL(1),
    IMPORTANT(2),
    LOSSY(3);

    private final int code;

    JournalEventClass(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
