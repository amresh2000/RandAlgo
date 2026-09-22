package com.penguinsecure.basis.journal.snapshot;

import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.oems.ChildOrderRole;
import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.ExecutionGroupState;
import com.penguinsecure.basis.core.oems.OemsStatus;
import com.penguinsecure.basis.core.recovery.CoreRecoveryState;
import com.penguinsecure.basis.core.recovery.CoreStateRestorer;
import com.penguinsecure.basis.core.recovery.RecoveryValidationStatus;
import com.penguinsecure.basis.core.risk.KillScope;
import com.penguinsecure.basis.core.risk.PartitionedTokenBucket;
import com.penguinsecure.basis.core.risk.RatePartition;
import com.penguinsecure.basis.core.risk.RiskReservationState;
import com.penguinsecure.basis.core.risk.RiskReservationStatus;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Canonical logical-state payload codec; internal hash/free-list layout is never persisted. */
public final class CoreSnapshotCodec {
    private static final int MAGIC = 0x43525331;
    private static final int PAYLOAD_VERSION = 1;

    public byte[] encode(final CoreRecoveryState state, final int maximumBytes) {
        if (state == null
                || maximumBytes <= 0
                || CoreStateRestorer.validate(state) != RecoveryValidationStatus.VALID) {
            throw new IllegalArgumentException("invalid state or payload bound");
        }
        try {
            final ByteBuffer out = ByteBuffer.allocate(maximumBytes).order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(MAGIC).putInt(PAYLOAD_VERSION);
            out.putInt(state.ledger().capacity())
                    .putInt(state.reservations().capacity())
                    .putInt(state.groups().capacity())
                    .putInt(state.children().capacity())
                    .putInt(state.children().executionCapacity())
                    .putInt(state.kills().maximumScopeId());
            out.putLong(state.nextJournalSequence()).putLong(state.appliedReplayEvents());
            encodeLedger(state, out);
            encodeReservations(state, out);
            encodeGroups(state, out);
            encodeChildren(state, out);
            encodeKills(state, out);
            return Arrays.copyOf(out.array(), out.position());
        } catch (BufferOverflowException exception) {
            throw new IllegalArgumentException(
                    "snapshot payload exceeds configured bound", exception);
        }
    }

    public SnapshotDecodeResult decode(final byte[] payload) {
        if (payload == null) return failure(SnapshotDecodeStatus.CORRUPT, "missing payload");
        try {
            final ByteBuffer in = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            if (in.getInt() != MAGIC || in.getInt() != PAYLOAD_VERSION) {
                return failure(SnapshotDecodeStatus.CORRUPT, "payload identity mismatch");
            }
            final int strategies = positive(in.getInt());
            final int reservations = positive(in.getInt());
            final int groups = positive(in.getInt());
            final int children = positive(in.getInt());
            final int executions = positive(in.getInt());
            final int killScopes = positive(in.getInt());
            final long nextSequence = nonnegative(in.getLong());
            final long replayEvents = nonnegative(in.getLong());
            final CoreRecoveryState state =
                    new CoreRecoveryState(
                            strategies, reservations, groups, children, executions, killScopes);
            decodeLedger(state, in);
            decodeReservations(state, in);
            decodeGroups(state, in);
            decodeChildren(state, in);
            decodeKills(state, in);
            state.restoreReplayProgress(nextSequence, replayEvents);
            if (in.hasRemaining())
                return failure(SnapshotDecodeStatus.CORRUPT, "trailing payload bytes");
            if (CoreStateRestorer.validate(state) != RecoveryValidationStatus.VALID) {
                return failure(SnapshotDecodeStatus.INVALID_STATE, "cross-table validation failed");
            }
            return new SnapshotDecodeResult(SnapshotDecodeStatus.DECODED, state, "validated");
        } catch (BufferUnderflowException
                | IllegalArgumentException
                | IllegalStateException exception) {
            return failure(SnapshotDecodeStatus.CORRUPT, exception.getClass().getSimpleName());
        }
    }

    private static void encodeLedger(final CoreRecoveryState state, final ByteBuffer out) {
        for (int slot = 0; slot < state.ledger().capacity(); slot++) {
            final int strategyId = state.ledger().strategyId(slot);
            out.put((byte) (strategyId == 0 ? 0 : 1));
            if (strategyId == 0) continue;
            out.putInt(strategyId)
                    .putLong(state.ledger().configurationGeneration(slot))
                    .putLong(state.ledger().confirmedGross(slot))
                    .putLong(state.ledger().pendingGross(slot))
                    .putLong(state.ledger().netExposure(slot))
                    .putLong(state.ledger().pendingNetExposure(slot))
                    .putLong(state.ledger().pendingUnhedgedExposure(slot))
                    .putLong(state.ledger().position(slot))
                    .putLong(state.ledger().reservedCollateral(slot))
                    .putLong(state.ledger().dailyLoss(slot))
                    .putInt(state.ledger().activeGroups(slot))
                    .putInt(state.ledger().unknownGroups(slot));
        }
    }

    private static void encodeReservations(final CoreRecoveryState state, final ByteBuffer out) {
        for (int slot = 0; slot < state.reservations().capacity(); slot++) {
            final int generation = state.reservations().generationAt(slot);
            final RiskReservationState reservationState = state.reservations().stateAt(slot);
            out.putInt(generation)
                    .put((byte) (reservationState == RiskReservationState.FREE ? 0 : 1));
            if (reservationState == RiskReservationState.FREE) continue;
            final PartitionedTokenBucket bucket = state.reservations().rateBucket(slot, generation);
            out.putInt(state.reservations().strategySlot(slot, generation))
                    .putLong(state.reservations().configurationGeneration(slot, generation))
                    .putLong(state.reservations().remainingGross(slot, generation))
                    .putLong(state.reservations().reservedNetExposure(slot, generation))
                    .putLong(state.reservations().reservedUnhedgedExposure(slot, generation))
                    .putLong(state.reservations().collateral(slot, generation))
                    .putLong(state.reservations().remainingHedgeClaims(slot, generation))
                    .putInt(reservationCode(reservationState));
            for (RatePartition partition : RatePartition.values()) {
                out.putLong(bucket.capacity(partition))
                        .putLong(bucket.tokensWithoutRefill(partition))
                        .putLong(bucket.refillTokens(partition));
            }
            out.putLong(bucket.refillIntervalNanos()).putLong(bucket.reservedHedgeTokens());
        }
    }

    private static void encodeGroups(final CoreRecoveryState state, final ByteBuffer out) {
        for (int slot = 0; slot < state.groups().capacity(); slot++) {
            final int generation = state.groups().generationAt(slot);
            final ExecutionGroupState groupState = state.groups().stateAt(slot);
            out.putInt(generation).put((byte) (groupState == ExecutionGroupState.FREE ? 0 : 1));
            if (groupState == ExecutionGroupState.FREE) continue;
            out.putInt(state.groups().strategyId(slot, generation))
                    .putLong(state.groups().configurationGeneration(slot, generation))
                    .putInt(state.groups().reservationSlot(slot, generation))
                    .putInt(state.groups().reservationGeneration(slot, generation))
                    .putLong(state.groups().initiationTarget(slot, generation))
                    .putLong(state.groups().hedgeTarget(slot, generation))
                    .putLong(state.groups().initiationFilled(slot, generation))
                    .putLong(state.groups().hedgeRequested(slot, generation))
                    .putLong(state.groups().hedgeFilled(slot, generation))
                    .putLong(state.groups().maximumImbalance(slot, generation))
                    .putInt(groupCode(groupState));
        }
    }

    private static void encodeChildren(final CoreRecoveryState state, final ByteBuffer out) {
        for (int slot = 0; slot < state.children().capacity(); slot++) {
            final int generation = state.children().generationAt(slot);
            final ChildOrderState childState = state.children().stateAt(slot);
            out.putInt(generation).put((byte) (childState == ChildOrderState.FREE ? 0 : 1));
            if (childState == ChildOrderState.FREE) continue;
            out.putLong(state.children().localOrderIdHigh(slot, generation))
                    .putLong(state.children().localOrderIdLow(slot, generation))
                    .putInt(state.children().groupSlot(slot, generation))
                    .putInt(roleCode(state.children().role(slot, generation)))
                    .putInt(state.children().venueId(slot, generation))
                    .putInt(state.children().instrumentId(slot, generation))
                    .putInt(sideCode(state.children().side(slot, generation)))
                    .putLong(state.children().quantity(slot, generation))
                    .putLong(state.children().filledQuantity(slot, generation))
                    .putLong(state.children().possibleOutstanding(slot, generation))
                    .putInt(childCode(childState));
        }
        final int countPosition = out.position();
        out.putInt(0);
        final int[] count = {0};
        state.children()
                .visitExecutions(
                        (identity, quantity, price, child, generation) -> {
                            out.putLong(identity)
                                    .putLong(quantity)
                                    .putLong(price)
                                    .putInt(child)
                                    .putInt(generation);
                            count[0]++;
                        });
        out.putInt(countPosition, count[0]);
    }

    private static void encodeKills(final CoreRecoveryState state, final ByteBuffer out) {
        for (KillScope scope : KillScope.values()) {
            for (int scopeId = 0; scopeId <= state.kills().maximumScopeId(); scopeId++) {
                final long generation = state.kills().generation(scope, scopeId);
                out.putLong(generation)
                        .put((byte) (state.kills().isKilled(scope, scopeId) ? 1 : 0));
            }
        }
    }

    private static void decodeLedger(final CoreRecoveryState state, final ByteBuffer in) {
        for (int slot = 0; slot < state.ledger().capacity(); slot++) {
            if (flag(in) == 0) continue;
            final int strategyId = in.getInt();
            final long generation = in.getLong();
            final long confirmed = in.getLong();
            final long pending = in.getLong();
            final long net = in.getLong();
            final long pendingNet = in.getLong();
            final long unhedged = in.getLong();
            final long position = in.getLong();
            final long collateral = in.getLong();
            final long loss = in.getLong();
            final int active = in.getInt();
            in.getInt();
            state.ledger()
                    .restoreSlot(
                            slot,
                            strategyId,
                            generation,
                            confirmed,
                            pending,
                            net,
                            pendingNet,
                            unhedged,
                            position,
                            collateral,
                            loss,
                            active,
                            active);
        }
    }

    private static void decodeReservations(final CoreRecoveryState state, final ByteBuffer in) {
        for (int slot = 0; slot < state.reservations().capacity(); slot++) {
            final int generation = in.getInt();
            if (flag(in) == 0) {
                if (generation > 0
                        && state.reservations().restoreFree(slot, generation)
                                != RiskReservationStatus.OK) {
                    throw new IllegalStateException("free reservation");
                }
                continue;
            }
            final int strategySlot = in.getInt();
            final long configurationGeneration = in.getLong();
            final long gross = in.getLong();
            final long net = in.getLong();
            final long unhedged = in.getLong();
            final long collateral = in.getLong();
            final long claims = in.getLong();
            final RiskReservationState reservationState = reservationState(in.getInt());
            final long[] capacities = new long[3];
            final long[] tokens = new long[3];
            final long[] refills = new long[3];
            for (int partition = 0; partition < 3; partition++) {
                capacities[partition] = in.getLong();
                tokens[partition] = in.getLong();
                refills[partition] = in.getLong();
            }
            final long interval = in.getLong();
            final long reserved = in.getLong();
            final PartitionedTokenBucket bucket =
                    new PartitionedTokenBucket(
                            capacities[0],
                            capacities[1],
                            capacities[2],
                            refills[0],
                            refills[1],
                            refills[2],
                            interval,
                            1);
            bucket.restoreConservatively(tokens[0], tokens[1], tokens[2], reserved);
            final RiskReservationStatus status =
                    state.reservations()
                            .restoreSlot(
                                    slot,
                                    generation,
                                    strategySlot,
                                    configurationGeneration,
                                    gross,
                                    net,
                                    unhedged,
                                    collateral,
                                    claims,
                                    reservationState,
                                    bucket);
            if (status != RiskReservationStatus.OK) throw new IllegalStateException("reservation");
        }
    }

    private static void decodeGroups(final CoreRecoveryState state, final ByteBuffer in) {
        for (int slot = 0; slot < state.groups().capacity(); slot++) {
            final int generation = in.getInt();
            if (flag(in) == 0) {
                if (generation > 0
                        && state.groups().restoreFree(slot, generation) != OemsStatus.OK) {
                    throw new IllegalStateException("free group");
                }
                continue;
            }
            final OemsStatus status =
                    state.groups()
                            .restoreSlot(
                                    slot,
                                    generation,
                                    in.getInt(),
                                    in.getLong(),
                                    in.getInt(),
                                    in.getInt(),
                                    in.getLong(),
                                    in.getLong(),
                                    in.getLong(),
                                    in.getLong(),
                                    in.getLong(),
                                    in.getLong(),
                                    groupState(in.getInt()));
            if (status != OemsStatus.OK) throw new IllegalStateException("group");
        }
    }

    private static void decodeChildren(final CoreRecoveryState state, final ByteBuffer in) {
        for (int slot = 0; slot < state.children().capacity(); slot++) {
            final int generation = in.getInt();
            if (flag(in) == 0) {
                if (generation > 0
                        && state.children().restoreFree(slot, generation) != OemsStatus.OK) {
                    throw new IllegalStateException("free child");
                }
                continue;
            }
            final OemsStatus status =
                    state.children()
                            .restoreSlot(
                                    slot,
                                    generation,
                                    in.getLong(),
                                    in.getLong(),
                                    in.getInt(),
                                    role(in.getInt()),
                                    in.getInt(),
                                    in.getInt(),
                                    side(in.getInt()),
                                    in.getLong(),
                                    in.getLong(),
                                    in.getLong(),
                                    childState(in.getInt()));
            if (status != OemsStatus.OK) throw new IllegalStateException("child");
        }
        final int executions = nonnegative(in.getInt());
        if (executions > state.children().executionCapacity()) {
            throw new IllegalArgumentException("execution capacity mismatch");
        }
        for (int index = 0; index < executions; index++) {
            final OemsStatus status =
                    state.children()
                            .restoreExecution(
                                    in.getLong(),
                                    in.getLong(),
                                    in.getLong(),
                                    in.getInt(),
                                    in.getInt());
            if (status != OemsStatus.OK) throw new IllegalStateException("execution");
        }
    }

    private static void decodeKills(final CoreRecoveryState state, final ByteBuffer in) {
        for (KillScope scope : KillScope.values()) {
            for (int scopeId = 0; scopeId <= state.kills().maximumScopeId(); scopeId++) {
                final long generation = in.getLong();
                final boolean killed = flag(in) == 1;
                if (generation > 0) state.kills().restore(scope, scopeId, generation, killed);
            }
        }
    }

    private static int flag(final ByteBuffer in) {
        final int value = Byte.toUnsignedInt(in.get());
        if (value > 1) throw new IllegalArgumentException("invalid flag");
        return value;
    }

    private static int positive(final int value) {
        if (value <= 0) throw new IllegalArgumentException("invalid capacity");
        return value;
    }

    private static int nonnegative(final int value) {
        if (value < 0) throw new IllegalArgumentException("negative value");
        return value;
    }

    private static long nonnegative(final long value) {
        if (value < 0) throw new IllegalArgumentException("negative value");
        return value;
    }

    private static SnapshotDecodeResult failure(
            final SnapshotDecodeStatus status, final String diagnostic) {
        return new SnapshotDecodeResult(status, null, diagnostic);
    }

    private static int reservationCode(final RiskReservationState state) {
        return switch (state) {
            case FREE -> 1;
            case RESERVED -> 2;
            case SENT -> 3;
            case UNKNOWN -> 4;
            case RECONCILING -> 5;
        };
    }

    private static RiskReservationState reservationState(final int code) {
        return switch (code) {
            case 1 -> RiskReservationState.FREE;
            case 2 -> RiskReservationState.RESERVED;
            case 3 -> RiskReservationState.SENT;
            case 4 -> RiskReservationState.UNKNOWN;
            case 5 -> RiskReservationState.RECONCILING;
            default -> throw new IllegalArgumentException("unknown reservation state");
        };
    }

    private static int groupCode(final ExecutionGroupState state) {
        return switch (state) {
            case FREE -> 1;
            case PLANNED -> 2;
            case RESERVED -> 3;
            case INITIATING -> 4;
            case HEDGING -> 5;
            case BALANCED -> 6;
            case UNWINDING -> 7;
            case UNKNOWN -> 8;
            case FAILED -> 9;
            case COMPLETE -> 10;
        };
    }

    private static ExecutionGroupState groupState(final int code) {
        return switch (code) {
            case 1 -> ExecutionGroupState.FREE;
            case 2 -> ExecutionGroupState.PLANNED;
            case 3 -> ExecutionGroupState.RESERVED;
            case 4 -> ExecutionGroupState.INITIATING;
            case 5 -> ExecutionGroupState.HEDGING;
            case 6 -> ExecutionGroupState.BALANCED;
            case 7 -> ExecutionGroupState.UNWINDING;
            case 8 -> ExecutionGroupState.UNKNOWN;
            case 9 -> ExecutionGroupState.FAILED;
            case 10 -> ExecutionGroupState.COMPLETE;
            default -> throw new IllegalArgumentException("unknown group state");
        };
    }

    private static int childCode(final ChildOrderState state) {
        return switch (state) {
            case FREE -> 1;
            case CREATED -> 2;
            case SEND_PENDING -> 3;
            case SENT -> 4;
            case ACKNOWLEDGED -> 5;
            case WORKING -> 6;
            case PARTIALLY_FILLED -> 7;
            case FILLED -> 8;
            case CANCEL_PENDING -> 9;
            case CANCELLED -> 10;
            case REJECTED -> 11;
            case UNKNOWN -> 12;
            case RECONCILING -> 13;
            case FAULTED -> 14;
        };
    }

    private static ChildOrderState childState(final int code) {
        return switch (code) {
            case 1 -> ChildOrderState.FREE;
            case 2 -> ChildOrderState.CREATED;
            case 3 -> ChildOrderState.SEND_PENDING;
            case 4 -> ChildOrderState.SENT;
            case 5 -> ChildOrderState.ACKNOWLEDGED;
            case 6 -> ChildOrderState.WORKING;
            case 7 -> ChildOrderState.PARTIALLY_FILLED;
            case 8 -> ChildOrderState.FILLED;
            case 9 -> ChildOrderState.CANCEL_PENDING;
            case 10 -> ChildOrderState.CANCELLED;
            case 11 -> ChildOrderState.REJECTED;
            case 12 -> ChildOrderState.UNKNOWN;
            case 13 -> ChildOrderState.RECONCILING;
            case 14 -> ChildOrderState.FAULTED;
            default -> throw new IllegalArgumentException("unknown child state");
        };
    }

    private static int roleCode(final ChildOrderRole role) {
        return switch (role) {
            case INITIATION -> 1;
            case HEDGE -> 2;
            case UNWIND -> 3;
        };
    }

    private static ChildOrderRole role(final int code) {
        return switch (code) {
            case 1 -> ChildOrderRole.INITIATION;
            case 2 -> ChildOrderRole.HEDGE;
            case 3 -> ChildOrderRole.UNWIND;
            default -> throw new IllegalArgumentException("unknown child role");
        };
    }

    private static int sideCode(final OrderSide side) {
        return switch (side) {
            case BUY -> 1;
            case SELL -> 2;
        };
    }

    private static OrderSide side(final int code) {
        return switch (code) {
            case 1 -> OrderSide.BUY;
            case 2 -> OrderSide.SELL;
            default -> throw new IllegalArgumentException("unknown order side");
        };
    }
}
