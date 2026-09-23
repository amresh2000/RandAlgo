package com.penguinsecure.basis.app.operator;

@FunctionalInterface
public interface OperatorCommandHandler {
    void onCommand(MutableOperatorCommand command);
}
