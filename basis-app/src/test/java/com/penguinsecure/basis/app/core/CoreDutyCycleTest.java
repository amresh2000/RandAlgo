package com.penguinsecure.basis.app.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class CoreDutyCycleTest {
    @Test
    void preservesPriorityWhileServicingEveryFairSource() {
        final List<String> order = new ArrayList<>();
        final Source health = new Source("health", order, 0);
        final Source urgent = new Source("urgent", order, 0);
        final Source operator = new Source("operator", order, 0);
        final Source firstMarketData = new Source("market-1", order, 200);
        final Source secondMarketData = new Source("market-2", order, 0);
        final Source background = new Source("background", order, 0);
        final CoreDutyCycle dutyCycle =
                new CoreDutyCycle(
                        health,
                        urgent,
                        new BoundedWorkSource[] {operator},
                        new BoundedWorkSource[] {firstMarketData, secondMarketData},
                        new BoundedWorkSource[] {background},
                        () -> 1_000,
                        8,
                        4,
                        2,
                        1,
                        100);

        dutyCycle.doWork();
        assertEquals(
                List.of(
                        "health:1",
                        "urgent:8",
                        "operator:4",
                        "market-1:2",
                        "market-2:2",
                        "background:1"),
                order);
        assertEquals(1, dutyCycle.starvationCount(0));
        assertEquals(0, dutyCycle.starvationCount(1));

        order.clear();
        dutyCycle.doWork();
        assertEquals("market-2:2", order.get(3));
        assertEquals("market-1:2", order.get(4));
    }

    private static final class Source implements BoundedWorkSource {
        private final String name;
        private final List<String> order;
        private final long age;

        private Source(final String name, final List<String> order, final long age) {
            this.name = name;
            this.order = order;
            this.age = age;
        }

        @Override
        public int doWork(final int quota) {
            order.add(name + ":" + quota);
            return quota;
        }

        @Override
        public long oldestAgeNanos(final long nowNanos) {
            return age;
        }
    }
}
