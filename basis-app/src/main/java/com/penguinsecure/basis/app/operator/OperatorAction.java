package com.penguinsecure.basis.app.operator;

public enum OperatorAction {
    STATUS(false, OperatorRole.VIEWER),
    ARM(true, OperatorRole.TRADER),
    DISARM(true, OperatorRole.TRADER),
    KILL(true, OperatorRole.RISK),
    CANCEL_ALL(true, OperatorRole.TRADER),
    RECONCILE(true, OperatorRole.OPERATOR),
    SNAPSHOT(true, OperatorRole.OPERATOR),
    STAGE_CONFIG(true, OperatorRole.ADMIN),
    ACTIVATE_CONFIG(true, OperatorRole.ADMIN),
    SHUTDOWN(true, OperatorRole.ADMIN);

    private final boolean mutating;
    private final OperatorRole minimumRole;

    OperatorAction(final boolean mutating, final OperatorRole minimumRole) {
        this.mutating = mutating;
        this.minimumRole = minimumRole;
    }

    public boolean mutating() {
        return mutating;
    }

    public boolean authorized(final OperatorRole role) {
        return role != null && role.authority() >= minimumRole.authority();
    }
}
