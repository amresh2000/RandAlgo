package com.penguinsecure.basis.core.oems;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.command.OrderSide;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class ChildOrderTableTest {
    @Test
    void duplicateExecutionIsIdempotentButChangedContentFaults() {
        ChildOrderTable table = new ChildOrderTable(2, 4);
        MutableSlotHandle child = new MutableSlotHandle();
        assertEquals(
                OemsStatus.OK,
                table.create(1, 2, 0, ChildOrderRole.INITIATION, 1, 2, OrderSide.BUY, 100, child));
        table.markSendPending(child.slot(), child.generation());
        table.markSent(child.slot(), child.generation());
        assertEquals(OemsStatus.OK, table.applyFill(child.slot(), child.generation(), 99, 40, 100));
        assertEquals(
                OemsStatus.DUPLICATE,
                table.applyFill(child.slot(), child.generation(), 99, 40, 100));
        assertEquals(
                OemsStatus.CONFLICT,
                table.applyFill(child.slot(), child.generation(), 99, 41, 100));
        assertEquals(ChildOrderState.FAULTED, table.state(child.slot(), child.generation()));
        assertEquals(60, table.possibleOutstanding(child.slot(), child.generation()));
    }

    @Test
    void ambiguityRetainsMaximumPossibleOutstandingUntilReconciled() {
        ChildOrderTable table = new ChildOrderTable(1, 2);
        MutableSlotHandle child = new MutableSlotHandle();
        table.create(1, 3, 0, ChildOrderRole.HEDGE, 1, 2, OrderSide.SELL, 100, child);
        table.markSendPending(child.slot(), child.generation());
        table.markUnknown(child.slot(), child.generation());
        assertEquals(100, table.possibleOutstanding(child.slot(), child.generation()));
        table.beginReconciliation(child.slot(), child.generation());
        assertEquals(
                OemsStatus.OK,
                table.reconcile(child.slot(), child.generation(), 30, ChildOrderState.CANCELLED));
        assertEquals(70, table.possibleOutstanding(child.slot(), child.generation()));
    }

    @Test
    void cancelAndRejectTransitionsAreIdempotentAndTerminal() {
        ChildOrderTable table = new ChildOrderTable(2, 2);
        MutableSlotHandle cancelled = new MutableSlotHandle();
        table.create(1, 4, 0, ChildOrderRole.HEDGE, 1, 2, OrderSide.SELL, 100, cancelled);
        table.markSendPending(cancelled.slot(), cancelled.generation());
        table.markSent(cancelled.slot(), cancelled.generation());
        assertEquals(OemsStatus.OK, table.requestCancel(cancelled.slot(), cancelled.generation()));
        assertEquals(OemsStatus.OK, table.cancel(cancelled.slot(), cancelled.generation()));
        assertEquals(OemsStatus.DUPLICATE, table.cancel(cancelled.slot(), cancelled.generation()));

        MutableSlotHandle rejected = new MutableSlotHandle();
        table.create(1, 5, 0, ChildOrderRole.HEDGE, 1, 2, OrderSide.SELL, 100, rejected);
        table.markSendPending(rejected.slot(), rejected.generation());
        table.markSent(rejected.slot(), rejected.generation());
        assertEquals(OemsStatus.OK, table.reject(rejected.slot(), rejected.generation()));
        assertEquals(0, table.possibleOutstanding(rejected.slot(), rejected.generation()));
    }

    @Test
    void terminalSlotReuseRejectsStaleGeneration() {
        ChildOrderTable table = new ChildOrderTable(1, 2);
        MutableSlotHandle stale = new MutableSlotHandle();
        table.create(1, 6, 0, ChildOrderRole.INITIATION, 1, 2, OrderSide.BUY, 10, stale);
        table.markSendPending(stale.slot(), stale.generation());
        table.markSent(stale.slot(), stale.generation());
        table.applyFill(stale.slot(), stale.generation(), 300, 10, 100);
        assertEquals(OemsStatus.OK, table.releaseTerminal(stale.slot(), stale.generation()));

        MutableSlotHandle current = new MutableSlotHandle();
        assertEquals(
                OemsStatus.OK,
                table.create(1, 7, 0, ChildOrderRole.INITIATION, 1, 2, OrderSide.BUY, 10, current));
        assertEquals(OemsStatus.STALE_HANDLE, table.markSent(stale.slot(), stale.generation()));
    }
}
