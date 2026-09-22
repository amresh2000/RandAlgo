package com.penguinsecure.basis.venue.bybit.order;

public final class MutableBybitPrivateControl {
    private BybitPrivateControlKind kind;

    void kind(final BybitPrivateControlKind value) {
        kind = value;
    }

    public BybitPrivateControlKind kind() {
        return kind;
    }
}
