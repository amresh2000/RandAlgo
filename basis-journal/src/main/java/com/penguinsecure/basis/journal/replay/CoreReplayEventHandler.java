package com.penguinsecure.basis.journal.replay;

import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.oems.ChildOrderRole;
import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.ExecutionGroupState;
import com.penguinsecure.basis.core.oems.MutableSlotHandle;
import com.penguinsecure.basis.core.oems.OemsStatus;
import com.penguinsecure.basis.core.recovery.CoreRecoveryState;
import com.penguinsecure.basis.core.risk.KillScope;
import com.penguinsecure.basis.core.risk.KillUpdateStatus;
import com.penguinsecure.basis.core.risk.PartitionedTokenBucket;
import com.penguinsecure.basis.core.risk.RiskReservationState;
import com.penguinsecure.basis.core.risk.RiskReservationStatus;
import com.penguinsecure.basis.protocol.sbe.ExecutionGroupDecoder;
import com.penguinsecure.basis.protocol.sbe.FillDecoder;
import com.penguinsecure.basis.protocol.sbe.KillStateDecoder;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderDecoder;
import com.penguinsecure.basis.protocol.sbe.OrderFactDecoder;
import com.penguinsecure.basis.protocol.sbe.OrderLifecycle;
import com.penguinsecure.basis.protocol.sbe.OrderStateDecoder;
import com.penguinsecure.basis.protocol.sbe.PositionDecoder;
import com.penguinsecure.basis.protocol.sbe.ReservationLifecycle;
import com.penguinsecure.basis.protocol.sbe.RiskReservationStateDecoder;
import org.agrona.DirectBuffer;

/** Applies full-after-state durable facts to fresh recovery state without outbound side effects. */
public final class CoreReplayEventHandler implements ReplayEventHandler {
    private final CoreRecoveryState state;
    private final RecoveryRateBucketFactory bucketFactory;
    private final MessageHeaderDecoder header = new MessageHeaderDecoder();
    private final PositionDecoder position = new PositionDecoder();
    private final RiskReservationStateDecoder reservation = new RiskReservationStateDecoder();
    private final ExecutionGroupDecoder group = new ExecutionGroupDecoder();
    private final OrderStateDecoder order = new OrderStateDecoder();
    private final OrderFactDecoder orderFact = new OrderFactDecoder();
    private final FillDecoder fill = new FillDecoder();
    private final KillStateDecoder kill = new KillStateDecoder();
    private final MutableSlotHandle resolvedChild = new MutableSlotHandle();

    public CoreReplayEventHandler(
            final CoreRecoveryState state, final RecoveryRateBucketFactory bucketFactory) {
        if (state == null || bucketFactory == null) {
            throw new IllegalArgumentException("state and rate-bucket factory required");
        }
        this.state = state;
        this.bucketFactory = bucketFactory;
    }

    @Override
    public boolean onEvent(
            final long eventSequence,
            final int templateId,
            final DirectBuffer buffer,
            final int offset,
            final int length) {
        header.wrap(buffer, offset);
        final int body = offset + MessageHeaderDecoder.ENCODED_LENGTH;
        try {
            final boolean applied =
                    switch (templateId) {
                        case PositionDecoder.TEMPLATE_ID -> applyPosition(buffer, body);
                        case RiskReservationStateDecoder.TEMPLATE_ID ->
                                applyReservation(buffer, body);
                        case ExecutionGroupDecoder.TEMPLATE_ID -> applyGroup(buffer, body);
                        case OrderStateDecoder.TEMPLATE_ID -> applyOrder(buffer, body);
                        case OrderFactDecoder.TEMPLATE_ID -> applyOrderFact(buffer, body);
                        case FillDecoder.TEMPLATE_ID -> applyFill(buffer, body);
                        case KillStateDecoder.TEMPLATE_ID -> applyKill(buffer, body);
                        default -> true;
                    };
            if (applied) state.eventApplied(eventSequence);
            return applied;
        } catch (RuntimeException malformedFact) {
            return false;
        }
    }

    private boolean applyPosition(final DirectBuffer buffer, final int body) {
        position.wrap(buffer, body, header.blockLength(), header.version());
        final int slot = (int) position.strategySlot();
        final int active = (int) position.activeGroups();
        try {
            state.ledger()
                    .restoreSlot(
                            slot,
                            (int) position.eventHeader().strategyId(),
                            position.eventHeader().configurationGeneration(),
                            position.confirmedGross(),
                            position.pendingGross(),
                            position.netExposure(),
                            position.pendingNetExposure(),
                            position.pendingUnhedgedExposure(),
                            position.positionQuantity(),
                            position.reservedCollateral(),
                            position.dailyLoss(),
                            active,
                            Math.max(active, (int) position.unknownGroups()));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean applyReservation(final DirectBuffer buffer, final int body) {
        reservation.wrap(buffer, body, header.blockLength(), header.version());
        if (reservation.lifecycle() == ReservationLifecycle.FREE) {
            return state.reservations()
                            .restoreFree(
                                    (int) reservation.reservationSlot(),
                                    (int) reservation.reservationGeneration())
                    == RiskReservationStatus.OK;
        }
        final int strategySlot = (int) reservation.strategySlot();
        final long generation = reservation.configurationGeneration();
        final PartitionedTokenBucket bucket = bucketFactory.create(strategySlot, generation);
        if (bucket == null) return false;
        try {
            bucket.restoreConservatively(
                    reservation.normalTokens(),
                    reservation.hedgeTokens(),
                    reservation.emergencyTokens(),
                    reservation.reservedHedgeTokens());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        final RiskReservationStatus result =
                state.reservations()
                        .restoreSlot(
                                (int) reservation.reservationSlot(),
                                (int) reservation.reservationGeneration(),
                                strategySlot,
                                generation,
                                reservation.remainingGross(),
                                reservation.reservedNetExposure(),
                                reservation.reservedUnhedgedExposure(),
                                reservation.collateral(),
                                reservation.remainingHedgeClaims(),
                                reservationState(reservation.lifecycle()),
                                bucket);
        return result == RiskReservationStatus.OK;
    }

    private boolean applyGroup(final DirectBuffer buffer, final int body) {
        group.wrap(buffer, body, header.blockLength(), header.version());
        if (group.stateCode() == 1) {
            return state.groups()
                            .restoreFree((int) group.groupSlot(), (int) group.groupGeneration())
                    == OemsStatus.OK;
        }
        return state.groups()
                        .restoreSlot(
                                (int) group.groupSlot(),
                                (int) group.groupGeneration(),
                                (int) group.eventHeader().strategyId(),
                                group.configurationGeneration(),
                                (int) group.reservationSlot(),
                                (int) group.reservationGeneration(),
                                group.initiationTarget(),
                                group.hedgeTarget(),
                                group.initiatingFilledQuantity(),
                                group.hedgeRequested(),
                                group.hedgedQuantity(),
                                group.maximumImbalance(),
                                groupState(group.stateCode()))
                == OemsStatus.OK;
    }

    private boolean applyOrder(final DirectBuffer buffer, final int body) {
        order.wrap(buffer, body, header.blockLength(), header.version());
        return state.children()
                        .restoreSlot(
                                (int) order.childSlot(),
                                (int) order.childGeneration(),
                                order.localOrderIdHigh(),
                                order.localOrderIdLow(),
                                (int) order.groupSlot(),
                                role(order.roleCode()),
                                order.eventHeader().venue().value(),
                                (int) order.eventHeader().instrumentId(),
                                side(order.side()),
                                order.originalQuantityLots(),
                                order.filledQuantityLots(),
                                order.possibleOutstanding(),
                                childState(order.lifecycle()))
                == OemsStatus.OK;
    }

    private boolean applyOrderFact(final DirectBuffer buffer, final int body) {
        orderFact.wrap(buffer, body, header.blockLength(), header.version());
        if (orderFact.executionIdentityHash() == 0) return true;
        if (state.children()
                        .resolve(
                                orderFact.localOrderIdHigh(),
                                orderFact.localOrderIdLow(),
                                resolvedChild)
                != OemsStatus.OK) return false;
        final OemsStatus result =
                state.children()
                        .restoreExecution(
                                orderFact.executionIdentityHash(),
                                orderFact.fillQuantity(),
                                orderFact.fillPriceTicks(),
                                resolvedChild.slot(),
                                resolvedChild.generation());
        return result == OemsStatus.OK || result == OemsStatus.DUPLICATE;
    }

    private boolean applyFill(final DirectBuffer buffer, final int body) {
        fill.wrap(buffer, body, header.blockLength(), header.version());
        if (state.children().resolve(fill.localOrderIdHigh(), fill.localOrderIdLow(), resolvedChild)
                != OemsStatus.OK) return false;
        final OemsStatus result =
                state.children()
                        .restoreExecution(
                                fill.venueFillIdHash(),
                                fill.quantityLots(),
                                fill.priceTicks(),
                                resolvedChild.slot(),
                                resolvedChild.generation());
        return result == OemsStatus.OK || result == OemsStatus.DUPLICATE;
    }

    private boolean applyKill(final DirectBuffer buffer, final int body) {
        kill.wrap(buffer, body, header.blockLength(), header.version());
        final KillScope scope =
                switch (kill.scopeType()) {
                    case GLOBAL -> KillScope.GLOBAL;
                    case VENUE -> KillScope.VENUE;
                    case ACCOUNT -> KillScope.ACCOUNT;
                    case STRATEGY -> KillScope.STRATEGY;
                    case INSTRUMENT -> KillScope.INSTRUMENT;
                    default -> null;
                };
        if (scope == null) return false;
        final KillUpdateStatus result =
                state.kills()
                        .restore(
                                scope, (int) kill.scopeId(), kill.generation(), kill.killed() != 0);
        return result == KillUpdateStatus.APPLIED || result == KillUpdateStatus.IDEMPOTENT;
    }

    private static RiskReservationState reservationState(final ReservationLifecycle lifecycle) {
        return switch (lifecycle) {
            case RESERVED -> RiskReservationState.RESERVED;
            case ACTIVE -> RiskReservationState.SENT;
            case UNKNOWN -> RiskReservationState.UNKNOWN;
            case RECONCILING -> RiskReservationState.RECONCILING;
            default -> throw new IllegalArgumentException("free reservation cannot be restored");
        };
    }

    private static ExecutionGroupState groupState(final int code) {
        return switch (code) {
            case 2 -> ExecutionGroupState.PLANNED;
            case 3 -> ExecutionGroupState.RESERVED;
            case 4 -> ExecutionGroupState.INITIATING;
            case 5 -> ExecutionGroupState.HEDGING;
            case 6 -> ExecutionGroupState.BALANCED;
            case 7 -> ExecutionGroupState.UNWINDING;
            case 8 -> ExecutionGroupState.UNKNOWN;
            case 9 -> ExecutionGroupState.FAILED;
            case 10 -> ExecutionGroupState.COMPLETE;
            default -> throw new IllegalArgumentException("invalid group state code");
        };
    }

    private static ChildOrderState childState(final OrderLifecycle lifecycle) {
        return switch (lifecycle) {
            case PENDING_SEND -> ChildOrderState.SEND_PENDING;
            case SENT -> ChildOrderState.SENT;
            case ACCEPTED -> ChildOrderState.ACKNOWLEDGED;
            case PARTIALLY_FILLED -> ChildOrderState.PARTIALLY_FILLED;
            case FILLED -> ChildOrderState.FILLED;
            case CANCELLED -> ChildOrderState.CANCELLED;
            case REJECTED -> ChildOrderState.REJECTED;
            case UNKNOWN -> ChildOrderState.UNKNOWN;
            default -> throw new IllegalArgumentException("invalid order lifecycle");
        };
    }

    private static ChildOrderRole role(final int code) {
        return switch (code) {
            case 1 -> ChildOrderRole.INITIATION;
            case 2 -> ChildOrderRole.HEDGE;
            case 3 -> ChildOrderRole.UNWIND;
            default -> throw new IllegalArgumentException("invalid child role");
        };
    }

    private static OrderSide side(final com.penguinsecure.basis.protocol.sbe.OrderSide side) {
        return switch (side) {
            case BUY -> OrderSide.BUY;
            case SELL -> OrderSide.SELL;
            default -> throw new IllegalArgumentException("invalid side");
        };
    }
}
