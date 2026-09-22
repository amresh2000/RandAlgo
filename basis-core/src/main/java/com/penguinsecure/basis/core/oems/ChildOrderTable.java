package com.penguinsecure.basis.core.oems;

import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;

/** Structure-of-arrays child-order store with bounded local-ID and execution-ID indexes. */
public final class ChildOrderTable {
    private final int[] generations;
    private final int[] nextFree;
    private final long[] idHigh;
    private final long[] idLow;
    private final int[] groupSlots;
    private final ChildOrderRole[] roles;
    private final int[] venueIds;
    private final int[] instrumentIds;
    private final OrderSide[] sides;
    private final long[] quantities;
    private final long[] filled;
    private final long[] possibleOutstanding;
    private final ChildOrderState[] states;
    private final long[] indexHigh;
    private final long[] indexLow;
    private final int[] indexSlots;
    private final long[] executionHashes;
    private final long[] executionQuantities;
    private final long[] executionPrices;
    private final int[] executionChildren;
    private final int[] executionChildGenerations;
    private final MutableLongResult arithmetic = new MutableLongResult();
    private int freeHead;

    public ChildOrderTable(final int capacity, final int executionCapacity) {
        if (capacity <= 0 || executionCapacity <= 0)
            throw new IllegalArgumentException("invalid capacity");
        generations = new int[capacity];
        nextFree = new int[capacity];
        idHigh = new long[capacity];
        idLow = new long[capacity];
        groupSlots = new int[capacity];
        roles = new ChildOrderRole[capacity];
        venueIds = new int[capacity];
        instrumentIds = new int[capacity];
        sides = new OrderSide[capacity];
        quantities = new long[capacity];
        filled = new long[capacity];
        possibleOutstanding = new long[capacity];
        states = new ChildOrderState[capacity];
        final int indexCapacity = tableCapacity(capacity * 2);
        indexHigh = new long[indexCapacity];
        indexLow = new long[indexCapacity];
        indexSlots = new int[indexCapacity];
        executionHashes = new long[tableCapacity(executionCapacity * 2)];
        executionQuantities = new long[executionHashes.length];
        executionPrices = new long[executionHashes.length];
        executionChildren = new int[executionHashes.length];
        executionChildGenerations = new int[executionHashes.length];
        for (int slot = 0; slot < capacity; slot++) {
            nextFree[slot] = slot + 1;
            states[slot] = ChildOrderState.FREE;
        }
        nextFree[capacity - 1] = -1;
        for (int index = 0; index < indexSlots.length; index++) indexSlots[index] = -1;
        for (int index = 0; index < executionChildren.length; index++)
            executionChildren[index] = -1;
    }

    @SuppressWarnings("ParameterNumber")
    public OemsStatus create(
            final long localIdHigh,
            final long localIdLow,
            final int groupSlot,
            final ChildOrderRole role,
            final int venueId,
            final int instrumentId,
            final OrderSide side,
            final long quantity,
            final MutableSlotHandle destination) {
        if (role == null || side == null || quantity <= 0 || destination == null) {
            return OemsStatus.INVALID_STATE;
        }
        if (find(localIdHigh, localIdLow) >= 0) return OemsStatus.DUPLICATE;
        if (freeHead < 0) return OemsStatus.CAPACITY_EXHAUSTED;
        final int slot = freeHead;
        final int index = emptyIdIndex(localIdHigh, localIdLow);
        if (index < 0) return OemsStatus.CAPACITY_EXHAUSTED;
        freeHead = nextFree[slot];
        int generation = generations[slot] + 1;
        if (generation == 0) generation = 1;
        generations[slot] = generation;
        idHigh[slot] = localIdHigh;
        idLow[slot] = localIdLow;
        groupSlots[slot] = groupSlot;
        roles[slot] = role;
        venueIds[slot] = venueId;
        instrumentIds[slot] = instrumentId;
        sides[slot] = side;
        quantities[slot] = quantity;
        filled[slot] = 0;
        possibleOutstanding[slot] = quantity;
        states[slot] = ChildOrderState.CREATED;
        indexHigh[index] = localIdHigh;
        indexLow[index] = localIdLow;
        indexSlots[index] = slot;
        destination.set(slot, generation);
        return OemsStatus.OK;
    }

    public OemsStatus markSendPending(final int slot, final int generation) {
        return transition(slot, generation, ChildOrderState.CREATED, ChildOrderState.SEND_PENDING);
    }

    public OemsStatus markSent(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == ChildOrderState.SENT) return OemsStatus.DUPLICATE;
        if (states[slot] != ChildOrderState.SEND_PENDING) return OemsStatus.INVALID_STATE;
        states[slot] = ChildOrderState.SENT;
        return OemsStatus.OK;
    }

    public OemsStatus writeFailed(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == ChildOrderState.REJECTED) return OemsStatus.DUPLICATE;
        if (states[slot] != ChildOrderState.SEND_PENDING) return OemsStatus.INVALID_STATE;
        states[slot] = ChildOrderState.REJECTED;
        possibleOutstanding[slot] = 0;
        return OemsStatus.OK;
    }

    public OemsStatus acknowledge(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == ChildOrderState.ACKNOWLEDGED
                || states[slot] == ChildOrderState.WORKING
                || states[slot] == ChildOrderState.PARTIALLY_FILLED
                || states[slot] == ChildOrderState.FILLED
                || states[slot] == ChildOrderState.CANCEL_PENDING
                || states[slot] == ChildOrderState.CANCELLED) {
            return OemsStatus.DUPLICATE;
        }
        if (states[slot] != ChildOrderState.SENT) return OemsStatus.INVALID_STATE;
        states[slot] = ChildOrderState.ACKNOWLEDGED;
        return OemsStatus.OK;
    }

    public OemsStatus markWorking(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == ChildOrderState.WORKING
                || states[slot] == ChildOrderState.PARTIALLY_FILLED
                || states[slot] == ChildOrderState.FILLED
                || states[slot] == ChildOrderState.CANCEL_PENDING
                || states[slot] == ChildOrderState.CANCELLED) return OemsStatus.DUPLICATE;
        if (states[slot] != ChildOrderState.ACKNOWLEDGED) return OemsStatus.INVALID_STATE;
        states[slot] = ChildOrderState.WORKING;
        return OemsStatus.OK;
    }

    public OemsStatus requestCancel(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == ChildOrderState.CANCEL_PENDING) return OemsStatus.DUPLICATE;
        if (states[slot] != ChildOrderState.SENT
                && states[slot] != ChildOrderState.ACKNOWLEDGED
                && states[slot] != ChildOrderState.WORKING
                && states[slot] != ChildOrderState.PARTIALLY_FILLED) {
            return OemsStatus.INVALID_STATE;
        }
        states[slot] = ChildOrderState.CANCEL_PENDING;
        return OemsStatus.OK;
    }

    public OemsStatus cancel(final int slot, final int generation) {
        return transition(
                slot, generation, ChildOrderState.CANCEL_PENDING, ChildOrderState.CANCELLED);
    }

    public OemsStatus reject(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == ChildOrderState.REJECTED) return OemsStatus.DUPLICATE;
        if (states[slot] != ChildOrderState.SENT && states[slot] != ChildOrderState.ACKNOWLEDGED) {
            return OemsStatus.INVALID_STATE;
        }
        states[slot] = ChildOrderState.REJECTED;
        possibleOutstanding[slot] = 0;
        return OemsStatus.OK;
    }

    public OemsStatus applyFill(
            final int slot,
            final int generation,
            final long executionIdentityHash,
            final long fillQuantity,
            final long fillPriceTicks) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (executionIdentityHash == 0 || fillQuantity <= 0 || fillPriceTicks <= 0) {
            return OemsStatus.INVALID_STATE;
        }
        final int existing = findExecution(executionIdentityHash);
        if (existing >= 0) {
            return executionChildren[existing] == slot
                            && executionChildGenerations[existing] == generation
                            && executionQuantities[existing] == fillQuantity
                            && executionPrices[existing] == fillPriceTicks
                    ? OemsStatus.DUPLICATE
                    : fault(slot);
        }
        if (!fillable(states[slot])) return OemsStatus.INVALID_STATE;
        if (CheckedDecimalMath.add(filled[slot], fillQuantity, arithmetic) != NumericStatus.OK
                || arithmetic.value() > quantities[slot]) return fault(slot);
        final int empty = emptyExecutionIndex(executionIdentityHash);
        if (empty < 0) return OemsStatus.CAPACITY_EXHAUSTED;
        executionHashes[empty] = executionIdentityHash;
        executionQuantities[empty] = fillQuantity;
        executionPrices[empty] = fillPriceTicks;
        executionChildren[empty] = slot;
        executionChildGenerations[empty] = generation;
        filled[slot] = arithmetic.value();
        possibleOutstanding[slot] = quantities[slot] - filled[slot];
        states[slot] =
                possibleOutstanding[slot] == 0
                        ? ChildOrderState.FILLED
                        : ChildOrderState.PARTIALLY_FILLED;
        return OemsStatus.OK;
    }

    public OemsStatus markUnknown(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == ChildOrderState.UNKNOWN) return OemsStatus.DUPLICATE;
        if (states[slot] == ChildOrderState.FILLED
                || states[slot] == ChildOrderState.CANCELLED
                || states[slot] == ChildOrderState.REJECTED) return OemsStatus.INVALID_STATE;
        states[slot] = ChildOrderState.UNKNOWN;
        return OemsStatus.OK;
    }

    public OemsStatus beginReconciliation(final int slot, final int generation) {
        return transition(slot, generation, ChildOrderState.UNKNOWN, ChildOrderState.RECONCILING);
    }

    public OemsStatus reconcile(
            final int slot,
            final int generation,
            final long authoritativeFilled,
            final ChildOrderState authoritativeState) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] != ChildOrderState.RECONCILING
                || authoritativeFilled < filled[slot]
                || authoritativeFilled > quantities[slot]
                || authoritativeState == null
                || authoritativeState == ChildOrderState.UNKNOWN
                || authoritativeState == ChildOrderState.RECONCILING)
            return OemsStatus.INVALID_STATE;
        filled[slot] = authoritativeFilled;
        possibleOutstanding[slot] = quantities[slot] - authoritativeFilled;
        states[slot] = authoritativeState;
        return OemsStatus.OK;
    }

    public ChildOrderState state(final int slot, final int generation) {
        return valid(slot, generation) ? states[slot] : ChildOrderState.FREE;
    }

    public long filledQuantity(final int slot, final int generation) {
        return valid(slot, generation) ? filled[slot] : 0;
    }

    public long possibleOutstanding(final int slot, final int generation) {
        return valid(slot, generation) ? possibleOutstanding[slot] : 0;
    }

    public int groupSlot(final int slot, final int generation) {
        return valid(slot, generation) ? groupSlots[slot] : -1;
    }

    public ChildOrderRole role(final int slot, final int generation) {
        return valid(slot, generation) ? roles[slot] : null;
    }

    public long localOrderIdHigh(final int slot, final int generation) {
        return valid(slot, generation) ? idHigh[slot] : 0;
    }

    public long localOrderIdLow(final int slot, final int generation) {
        return valid(slot, generation) ? idLow[slot] : 0;
    }

    public OemsStatus resolve(
            final long localIdHigh, final long localIdLow, final MutableSlotHandle destination) {
        if (destination == null) return OemsStatus.INVALID_STATE;
        final int slot = find(localIdHigh, localIdLow);
        if (slot < 0 || states[slot] == ChildOrderState.FREE) {
            destination.clear();
            return OemsStatus.STALE_HANDLE;
        }
        destination.set(slot, generations[slot]);
        return OemsStatus.OK;
    }

    public int capacity() {
        return states.length;
    }

    public int generationAt(final int slot) {
        return slot >= 0 && slot < states.length ? generations[slot] : 0;
    }

    public ChildOrderState stateAt(final int slot) {
        return slot >= 0 && slot < states.length ? states[slot] : ChildOrderState.FREE;
    }

    public int venueId(final int slot, final int generation) {
        return valid(slot, generation) ? venueIds[slot] : 0;
    }

    public int instrumentId(final int slot, final int generation) {
        return valid(slot, generation) ? instrumentIds[slot] : 0;
    }

    public OrderSide side(final int slot, final int generation) {
        return valid(slot, generation) ? sides[slot] : null;
    }

    public long quantity(final int slot, final int generation) {
        return valid(slot, generation) ? quantities[slot] : 0;
    }

    public OemsStatus abandonCreated(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] != ChildOrderState.CREATED && states[slot] != ChildOrderState.SEND_PENDING)
            return OemsStatus.INVALID_STATE;
        final int index = idIndex(idHigh[slot], idLow[slot]);
        if (index >= 0) indexSlots[index] = -2;
        states[slot] = ChildOrderState.FREE;
        nextFree[slot] = freeHead;
        freeHead = slot;
        return OemsStatus.OK;
    }

    public OemsStatus releaseTerminal(final int slot, final int generation) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] != ChildOrderState.FILLED
                && states[slot] != ChildOrderState.CANCELLED
                && states[slot] != ChildOrderState.REJECTED
                && states[slot] != ChildOrderState.FAULTED) return OemsStatus.INVALID_STATE;
        final int idIndex = idIndex(idHigh[slot], idLow[slot]);
        if (idIndex >= 0) indexSlots[idIndex] = -2;
        for (int index = 0; index < executionChildren.length; index++) {
            if (executionChildren[index] == slot
                    && executionChildGenerations[index] == generation) {
                executionChildren[index] = -2;
                executionChildGenerations[index] = 0;
            }
        }
        states[slot] = ChildOrderState.FREE;
        nextFree[slot] = freeHead;
        freeHead = slot;
        return OemsStatus.OK;
    }

    private OemsStatus transition(
            final int slot,
            final int generation,
            final ChildOrderState expected,
            final ChildOrderState next) {
        if (!valid(slot, generation)) return OemsStatus.STALE_HANDLE;
        if (states[slot] == next) return OemsStatus.DUPLICATE;
        if (states[slot] != expected) return OemsStatus.INVALID_STATE;
        states[slot] = next;
        return OemsStatus.OK;
    }

    private OemsStatus fault(final int slot) {
        states[slot] = ChildOrderState.FAULTED;
        return OemsStatus.CONFLICT;
    }

    private boolean valid(final int slot, final int generation) {
        return slot >= 0
                && slot < states.length
                && generations[slot] == generation
                && generation != 0
                && states[slot] != ChildOrderState.FREE;
    }

    private int find(final long high, final long low) {
        final int index = idIndex(high, low);
        return index < 0 ? -1 : indexSlots[index];
    }

    private int idIndex(final long high, final long low) {
        int index = hash(high ^ low) & (indexSlots.length - 1);
        for (int probes = 0; probes < indexSlots.length; probes++) {
            if (indexSlots[index] == -1) return -1;
            if (indexSlots[index] >= 0 && indexHigh[index] == high && indexLow[index] == low) {
                return index;
            }
            index = (index + 1) & (indexSlots.length - 1);
        }
        return -1;
    }

    private int emptyIdIndex(final long high, final long low) {
        int index = hash(high ^ low) & (indexSlots.length - 1);
        for (int probes = 0; probes < indexSlots.length; probes++) {
            if (indexSlots[index] < 0) return index;
            index = (index + 1) & (indexSlots.length - 1);
        }
        return -1;
    }

    private int findExecution(final long identity) {
        int index = hash(identity) & (executionChildren.length - 1);
        for (int probes = 0; probes < executionChildren.length; probes++) {
            if (executionChildren[index] == -1) return -1;
            if (executionChildren[index] >= 0 && executionHashes[index] == identity) return index;
            index = (index + 1) & (executionChildren.length - 1);
        }
        return -1;
    }

    private int emptyExecutionIndex(final long identity) {
        int index = hash(identity) & (executionChildren.length - 1);
        for (int probes = 0; probes < executionChildren.length; probes++) {
            if (executionChildren[index] < 0) return index;
            index = (index + 1) & (executionChildren.length - 1);
        }
        return -1;
    }

    private static boolean fillable(final ChildOrderState state) {
        return state == ChildOrderState.SENT
                || state == ChildOrderState.ACKNOWLEDGED
                || state == ChildOrderState.WORKING
                || state == ChildOrderState.PARTIALLY_FILLED
                || state == ChildOrderState.CANCEL_PENDING
                || state == ChildOrderState.UNKNOWN
                || state == ChildOrderState.RECONCILING;
    }

    private static int tableCapacity(final int minimum) {
        int result = 1;
        while (result < minimum) result <<= 1;
        return result;
    }

    private static int hash(final long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (int) mixed;
    }
}
