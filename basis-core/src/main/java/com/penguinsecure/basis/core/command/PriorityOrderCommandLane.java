package com.penguinsecure.basis.core.command;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Two bounded SPSC rings; urgent commands are drained before normal commands. */
public final class PriorityOrderCommandLane {
    private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);
    private final CommandRing normal;
    private final CommandRing urgent;
    private final MutableOrderCommand view = new MutableOrderCommand();

    public PriorityOrderCommandLane(final int normalCapacity, final int urgentCapacity) {
        normal = new CommandRing(normalCapacity, OrderUrgency.NORMAL);
        urgent = new CommandRing(urgentCapacity, OrderUrgency.URGENT);
    }

    @SuppressWarnings("ParameterNumber")
    public boolean tryPublish(
            final OrderCommandType type,
            final OrderUrgency urgency,
            final long localOrderIdHigh,
            final long localOrderIdLow,
            final int venueId,
            final int instrumentId,
            final OrderSide side,
            final long quantity,
            final long limitPriceTicks,
            final long nowMonoNanos) {
        if (type == null
                || urgency == null
                || side == null
                || quantity <= 0
                || limitPriceTicks <= 0
                || nowMonoNanos <= 0) return false;
        return (urgency == OrderUrgency.URGENT ? urgent : normal)
                .tryPublish(
                        type,
                        localOrderIdHigh,
                        localOrderIdLow,
                        venueId,
                        instrumentId,
                        side,
                        quantity,
                        limitPriceTicks,
                        nowMonoNanos);
    }

    public int drainPrioritized(final OrderCommandHandler handler, final int limit) {
        if (handler == null || limit <= 0) return 0;
        int drained = urgent.drain(handler, view, limit);
        if (drained < limit) drained += normal.drain(handler, view, limit - drained);
        return drained;
    }

    public int size(final OrderUrgency urgency) {
        return urgency == OrderUrgency.URGENT ? urgent.size() : normal.size();
    }

    public long failedClaims(final OrderUrgency urgency) {
        return urgency == OrderUrgency.URGENT ? urgent.failedClaims : normal.failedClaims;
    }

    public long oldestAgeNanos(final OrderUrgency urgency, final long nowMonoNanos) {
        return (urgency == OrderUrgency.URGENT ? urgent : normal).oldestAgeNanos(nowMonoNanos);
    }

    private static final class CommandRing {
        private final int capacity;
        private final OrderUrgency urgency;
        private final OrderCommandType[] types;
        private final long[] idHigh;
        private final long[] idLow;
        private final int[] venueIds;
        private final int[] instrumentIds;
        private final OrderSide[] sides;
        private final long[] quantities;
        private final long[] prices;
        private final long[] created;
        private final long[] published;
        private volatile long producerSequence;
        private volatile long consumerSequence;
        private volatile long failedClaims;

        private CommandRing(final int newCapacity, final OrderUrgency newUrgency) {
            if (newCapacity <= 0) throw new IllegalArgumentException("capacity must be positive");
            capacity = newCapacity;
            urgency = newUrgency;
            types = new OrderCommandType[capacity];
            idHigh = new long[capacity];
            idLow = new long[capacity];
            venueIds = new int[capacity];
            instrumentIds = new int[capacity];
            sides = new OrderSide[capacity];
            quantities = new long[capacity];
            prices = new long[capacity];
            created = new long[capacity];
            published = new long[capacity];
        }

        @SuppressWarnings("ParameterNumber")
        private boolean tryPublish(
                final OrderCommandType type,
                final long high,
                final long low,
                final int venue,
                final int instrument,
                final OrderSide side,
                final long quantity,
                final long price,
                final long now) {
            if (producerSequence - consumerSequence >= capacity) {
                failedClaims++;
                return false;
            }
            final int index = (int) (producerSequence % capacity);
            types[index] = type;
            idHigh[index] = high;
            idLow[index] = low;
            venueIds[index] = venue;
            instrumentIds[index] = instrument;
            sides[index] = side;
            quantities[index] = quantity;
            prices[index] = price;
            created[index] = now;
            LONGS.setRelease(published, index, producerSequence + 1);
            producerSequence++;
            return true;
        }

        private int drain(
                final OrderCommandHandler handler,
                final MutableOrderCommand destination,
                final int limit) {
            int count = 0;
            while (count < limit && consumerSequence < producerSequence) {
                final int index = (int) (consumerSequence % capacity);
                if ((long) LONGS.getAcquire(published, index) != consumerSequence + 1) break;
                destination.set(
                        types[index],
                        urgency,
                        idHigh[index],
                        idLow[index],
                        venueIds[index],
                        instrumentIds[index],
                        sides[index],
                        quantities[index],
                        prices[index],
                        created[index]);
                handler.onCommand(destination);
                LONGS.setRelease(published, index, 0L);
                consumerSequence++;
                count++;
            }
            return count;
        }

        private int size() {
            return (int) (producerSequence - consumerSequence);
        }

        private long oldestAgeNanos(final long now) {
            if (consumerSequence >= producerSequence) return 0;
            final long timestamp = created[(int) (consumerSequence % capacity)];
            return now >= timestamp ? now - timestamp : Long.MAX_VALUE;
        }
    }
}
