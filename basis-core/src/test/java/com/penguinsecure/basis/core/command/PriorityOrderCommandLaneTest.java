package com.penguinsecure.basis.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class PriorityOrderCommandLaneTest {
    @Test
    void urgentTrafficDrainsFirstAndBackpressureIsObservable() {
        PriorityOrderCommandLane lane = new PriorityOrderCommandLane(1, 1);
        assertTrue(publish(lane, OrderUrgency.NORMAL, 1));
        assertTrue(publish(lane, OrderUrgency.URGENT, 2));
        assertFalse(publish(lane, OrderUrgency.URGENT, 3));
        long[] order = new long[2];
        int[] index = {0};

        assertEquals(
                2,
                lane.drainPrioritized(command -> order[index[0]++] = command.localOrderIdLow(), 2));

        assertEquals(2, order[0]);
        assertEquals(1, order[1]);
        assertEquals(1, lane.failedClaims(OrderUrgency.URGENT));
    }

    private static boolean publish(
            final PriorityOrderCommandLane lane, final OrderUrgency urgency, final long id) {
        return lane.tryPublish(
                OrderCommandType.SUBMIT, urgency, 0, id, 1, 2, OrderSide.BUY, 10, 100, 1);
    }
}
