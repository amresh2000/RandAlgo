package com.penguinsecure.basis.journal.replay;

import org.agrona.DirectBuffer;

/** Applies one validated durable SBE event to fresh recovery state; never emits live commands. */
@FunctionalInterface
public interface ReplayEventHandler {
    boolean onEvent(
            long eventSequence, int templateId, DirectBuffer buffer, int offset, int length);
}
