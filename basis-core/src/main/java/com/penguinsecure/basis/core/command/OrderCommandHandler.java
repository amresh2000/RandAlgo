package com.penguinsecure.basis.core.command;

/** Synchronous lane consumer; the mutable view is valid only during the callback. */
@FunctionalInterface
public interface OrderCommandHandler {
    void onCommand(MutableOrderCommand command);
}
