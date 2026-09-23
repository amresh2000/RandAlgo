package com.penguinsecure.basis.app.assembly;

/** Non-lifecycle operator effects supplied by the execution runtime. */
public interface OperationalEffects {
    boolean cancelAll(int scopeType, int scopeId);

    boolean reconcile(int scopeType, int scopeId);

    boolean snapshot();
}
