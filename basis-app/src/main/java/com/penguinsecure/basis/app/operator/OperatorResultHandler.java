package com.penguinsecure.basis.app.operator;

@FunctionalInterface
public interface OperatorResultHandler {
    void onResult(MutableOperatorResult result);
}
