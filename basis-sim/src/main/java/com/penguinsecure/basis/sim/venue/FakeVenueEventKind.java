package com.penguinsecure.basis.sim.venue;

public enum FakeVenueEventKind {
    WRITE_ACCEPTED(101),
    WRITE_FAILED(102),
    WRITE_AMBIGUOUS(103),
    ACKNOWLEDGED(104),
    FILL(105),
    REJECTED(106),
    DISCONNECTED(107),
    RATE_LIMITED(108),
    CANCELLED(109),
    RECONCILED(110);

    private final int code;

    FakeVenueEventKind(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static FakeVenueEventKind fromCode(final int code) {
        for (FakeVenueEventKind kind : values()) if (kind.code == code) return kind;
        throw new IllegalArgumentException("unknown fake venue event");
    }
}
