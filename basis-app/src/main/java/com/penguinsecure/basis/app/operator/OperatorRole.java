package com.penguinsecure.basis.app.operator;

public enum OperatorRole {
    VIEWER(0),
    OPERATOR(1),
    TRADER(2),
    RISK(3),
    ADMIN(4);
    private final int authority;

    OperatorRole(final int authority) {
        this.authority = authority;
    }

    int authority() {
        return authority;
    }
}
