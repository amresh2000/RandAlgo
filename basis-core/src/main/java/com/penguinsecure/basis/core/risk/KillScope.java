package com.penguinsecure.basis.core.risk;

/** Ordered kill scopes checked before every exposure-increasing action. */
public enum KillScope {
    GLOBAL(0),
    VENUE(1),
    STRATEGY(2),
    INSTRUMENT(3),
    ACCOUNT(4),
    EXECUTION_GROUP(5),
    SESSION(6);

    private final int code;

    KillScope(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
