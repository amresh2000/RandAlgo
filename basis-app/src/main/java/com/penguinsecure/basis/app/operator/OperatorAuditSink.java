package com.penguinsecure.basis.app.operator;

/** Critical append-only audit seam. False means the mutation must not apply. */
@FunctionalInterface
public interface OperatorAuditSink {
    boolean audit(MutableOperatorCommand command);
}
