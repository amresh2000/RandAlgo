package com.penguinsecure.basis.app.operator;

/** Explicit application effects invoked only after audit admission. */
public interface OperatorActionPort {
    boolean cancelAll(int scopeType, int scopeId);

    boolean reconcile(int scopeType, int scopeId);

    boolean snapshot();

    boolean stageConfiguration(byte[] payload, int length, long generation);

    boolean activateConfiguration(long generation);

    boolean beginShutdown(long generation);
}
