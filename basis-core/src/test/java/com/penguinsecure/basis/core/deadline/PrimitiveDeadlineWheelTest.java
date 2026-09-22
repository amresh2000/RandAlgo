package com.penguinsecure.basis.core.deadline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class PrimitiveDeadlineWheelTest {
    @Test
    void boundsCapacityAndExpiryWork() {
        PrimitiveDeadlineWheel wheel = new PrimitiveDeadlineWheel(2);
        MutableDeadlineHandle first = new MutableDeadlineHandle();
        MutableDeadlineHandle second = new MutableDeadlineHandle();
        assertEquals(DeadlineStatus.OK, wheel.schedule(10, 100, 1, first));
        assertEquals(DeadlineStatus.OK, wheel.schedule(20, 200, 2, second));
        assertEquals(
                DeadlineStatus.CAPACITY_EXHAUSTED,
                wheel.schedule(30, 300, 3, new MutableDeadlineHandle()));

        long[] observedOwner = {-1L};
        assertEquals(
                1, wheel.expireDue(20, 1, 1, (owner, type, deadline) -> observedOwner[0] = owner));
        assertEquals(100L, observedOwner[0]);
        assertEquals(1, wheel.activeCount());
    }

    @Test
    void staleHandleCannotCancelReusedSlot() {
        PrimitiveDeadlineWheel wheel = new PrimitiveDeadlineWheel(1);
        MutableDeadlineHandle stale = new MutableDeadlineHandle();
        assertEquals(DeadlineStatus.OK, wheel.schedule(10, 1, 1, stale));
        assertEquals(DeadlineStatus.OK, wheel.cancel(stale));

        MutableDeadlineHandle current = new MutableDeadlineHandle();
        assertEquals(DeadlineStatus.OK, wheel.schedule(20, 2, 2, current));
        assertEquals(DeadlineStatus.STALE_HANDLE, wheel.cancel(stale));
        assertEquals(DeadlineStatus.OK, wheel.cancel(current));
    }

    @Test
    void cancelledAndFutureDeadlinesDoNotFire() {
        PrimitiveDeadlineWheel wheel = new PrimitiveDeadlineWheel(2);
        MutableDeadlineHandle cancelled = new MutableDeadlineHandle();
        wheel.schedule(5, 1, 1, cancelled);
        wheel.schedule(50, 2, 2, new MutableDeadlineHandle());
        wheel.cancel(cancelled);
        assertEquals(0, wheel.expireDue(10, 2, 2, (owner, type, deadline) -> {}));
    }

    @Test
    void supportsSignedMonotonicValuesAndWrapSafeComparison() {
        PrimitiveDeadlineWheel wheel = new PrimitiveDeadlineWheel(1);
        assertEquals(
                DeadlineStatus.OK,
                wheel.schedule(Long.MIN_VALUE + 5, 7, 3, new MutableDeadlineHandle()));
        assertEquals(0, wheel.expireDue(Long.MAX_VALUE - 5, 1, 1, (owner, type, deadline) -> {}));
        assertEquals(1, wheel.expireDue(Long.MIN_VALUE + 5, 1, 1, (owner, type, deadline) -> {}));
    }
}
