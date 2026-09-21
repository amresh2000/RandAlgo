package com.penguinsecure.basis.core.oems.fact;

/** Venue-neutral order lifecycle facts with stable numeric codes. */
public enum OrderFactType {
    WRITE_ACCEPTED(1),
    WRITE_FAILED(2),
    WRITE_AMBIGUOUS(3),
    ACKNOWLEDGED(4),
    WORKING(5),
    FILL(6),
    CANCELLED(7),
    REJECTED(8),
    DISCONNECTED(9),
    RATE_LIMITED(10),
    RECONCILED(11);

    private final int code;

    OrderFactType(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static OrderFactType fromCode(final int code) {
        return switch (code) {
            case 1 -> WRITE_ACCEPTED;
            case 2 -> WRITE_FAILED;
            case 3 -> WRITE_AMBIGUOUS;
            case 4 -> ACKNOWLEDGED;
            case 5 -> WORKING;
            case 6 -> FILL;
            case 7 -> CANCELLED;
            case 8 -> REJECTED;
            case 9 -> DISCONNECTED;
            case 10 -> RATE_LIMITED;
            case 11 -> RECONCILED;
            default -> throw new IllegalArgumentException("unknown order fact code");
        };
    }
}
