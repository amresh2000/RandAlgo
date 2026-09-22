package com.penguinsecure.basis.core.oems;

import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.command.OrderSide;
import com.penguinsecure.basis.core.command.OrderUrgency;
import com.penguinsecure.basis.core.command.PriorityOrderCommandLane;
import com.penguinsecure.basis.core.identity.LocalOrderIdCodec;
import com.penguinsecure.basis.core.identity.MutableLocalOrderId;
import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.risk.MutableRiskDecision;
import com.penguinsecure.basis.core.risk.PreTradeRiskEngine;
import com.penguinsecure.basis.core.risk.PreTradeRiskRequest;
import com.penguinsecure.basis.core.risk.RiskDecisionStatus;
import com.penguinsecure.basis.core.risk.RiskEnvelope;
import com.penguinsecure.basis.core.risk.RiskRejectReason;
import com.penguinsecure.basis.core.risk.RiskReservationStatus;
import com.penguinsecure.basis.core.risk.RiskReservationTable;

/** Aggressive IOC coordinator that hedges every incremental initiation fill urgently. */
public final class AggressiveExecutionEngine {
    private final PreTradeRiskEngine risk;
    private final RiskReservationTable reservations;
    private final ExecutionGroupTable groups;
    private final ChildOrderTable children;
    private final PriorityOrderCommandLane commands;
    private final MutableRiskDecision riskDecision = new MutableRiskDecision();
    private final MutableLocalOrderId localOrderId = new MutableLocalOrderId();
    private final MutableLongResult arithmeticScratch = new MutableLongResult();
    private final MutableLongResult arithmeticResult = new MutableLongResult();
    private long nextOrderSequence = 1;

    public AggressiveExecutionEngine(
            final PreTradeRiskEngine risk,
            final RiskReservationTable reservations,
            final ExecutionGroupTable groups,
            final ChildOrderTable children,
            final PriorityOrderCommandLane commands) {
        if (risk == null
                || reservations == null
                || groups == null
                || children == null
                || commands == null)
            throw new IllegalArgumentException("dependencies are required");
        this.risk = risk;
        this.reservations = reservations;
        this.groups = groups;
        this.children = children;
        this.commands = commands;
    }

    public void start(
            final PreTradeRiskRequest request,
            final RiskEnvelope envelope,
            final AggressiveExecutionPlan plan,
            final long nowMonoNanos,
            final MutableExecutionStart destination) {
        if (plan == null || destination == null || nowMonoNanos <= 0) {
            if (destination != null)
                destination.fail(OemsStatus.INVALID_STATE, RiskRejectReason.INVALID_ARGUMENT);
            return;
        }
        if (request == null
                || !request.matchesExecutionPlan(
                        plan.strategySlot(),
                        plan.strategyId(),
                        plan.configurationGeneration(),
                        plan.initiationVenueId(),
                        plan.hedgeVenueId(),
                        plan.initiationSessionGeneration(),
                        plan.hedgeSessionGeneration(),
                        plan.initiationInstrumentId(),
                        plan.hedgeInstrumentId(),
                        plan.initiationQuantity(),
                        plan.hedgeQuantity(),
                        plan.initiationLimitPriceTicks(),
                        plan.hedgeLimitPriceTicks(),
                        plan.deadlineMonoNanos())) {
            destination.fail(OemsStatus.CONFLICT, RiskRejectReason.INVALID_ARGUMENT);
            return;
        }
        risk.evaluate(request, envelope, riskDecision);
        if (riskDecision.status() != RiskDecisionStatus.APPROVED) {
            destination.fail(OemsStatus.INVALID_STATE, riskDecision.reason());
            return;
        }
        final int reservationSlot = riskDecision.reservation().slot();
        final int reservationGeneration = riskDecision.reservation().generation();
        OemsStatus status =
                groups.create(
                        plan.strategyId(),
                        plan.configurationGeneration(),
                        reservationSlot,
                        reservationGeneration,
                        plan.initiationQuantity(),
                        plan.hedgeQuantity(),
                        plan.maximumImbalance(),
                        plan.deadlineMonoNanos(),
                        destination.group());
        if (status != OemsStatus.OK) {
            reservations.releaseAuthoritatively(reservationSlot, reservationGeneration);
            destination.fail(status, RiskRejectReason.NONE);
            return;
        }
        encodeNext(
                plan.cellId(),
                plan.initiationVenueId(),
                plan.initiationSessionGeneration(),
                plan.strategySlot());
        status =
                children.create(
                        localOrderId.high(),
                        localOrderId.low(),
                        destination.group().slot(),
                        ChildOrderRole.INITIATION,
                        plan.initiationVenueId(),
                        plan.initiationInstrumentId(),
                        plan.initiationSide(),
                        plan.initiationQuantity(),
                        destination.initiation());
        if (status != OemsStatus.OK) {
            groups.abandonReserved(destination.group().slot(), destination.group().generation());
            reservations.releaseAuthoritatively(reservationSlot, reservationGeneration);
            destination.fail(status, RiskRejectReason.NONE);
            return;
        }
        final boolean published =
                commands.tryPublish(
                        OrderCommandType.SUBMIT,
                        OrderUrgency.NORMAL,
                        localOrderId.high(),
                        localOrderId.low(),
                        plan.initiationVenueId(),
                        plan.initiationInstrumentId(),
                        plan.initiationSide(),
                        plan.initiationQuantity(),
                        plan.initiationLimitPriceTicks(),
                        nowMonoNanos);
        if (!published) {
            children.abandonCreated(
                    destination.initiation().slot(), destination.initiation().generation());
            groups.abandonReserved(destination.group().slot(), destination.group().generation());
            reservations.releaseAuthoritatively(reservationSlot, reservationGeneration);
            destination.fail(OemsStatus.CAPACITY_EXHAUSTED, RiskRejectReason.NONE);
            return;
        }
        children.markSendPending(
                destination.initiation().slot(), destination.initiation().generation());
        groups.markInitiating(destination.group().slot(), destination.group().generation());
        destination.approved();
    }

    public OemsStatus onInitiationWritten(
            final int groupSlot,
            final int groupGeneration,
            final int childSlot,
            final int childGeneration) {
        if (!sameGroup(groupSlot, groupGeneration, childSlot, childGeneration)
                || children.role(childSlot, childGeneration) != ChildOrderRole.INITIATION) {
            return OemsStatus.CONFLICT;
        }
        final OemsStatus childStatus = children.markSent(childSlot, childGeneration);
        if (childStatus != OemsStatus.OK && childStatus != OemsStatus.DUPLICATE) return childStatus;
        final RiskReservationStatus reservationStatus =
                reservations.markSent(
                        groups.reservationSlot(groupSlot, groupGeneration),
                        groups.reservationGeneration(groupSlot, groupGeneration));
        return reservationStatus == RiskReservationStatus.OK
                ? OemsStatus.OK
                : OemsStatus.INVALID_STATE;
    }

    public OemsStatus onWriteAmbiguous(
            final int groupSlot,
            final int groupGeneration,
            final int childSlot,
            final int childGeneration) {
        if (!sameGroup(groupSlot, groupGeneration, childSlot, childGeneration)) {
            return OemsStatus.CONFLICT;
        }
        final OemsStatus childStatus = children.markUnknown(childSlot, childGeneration);
        if (childStatus != OemsStatus.OK && childStatus != OemsStatus.DUPLICATE) return childStatus;
        final OemsStatus groupStatus = groups.markUnknown(groupSlot, groupGeneration);
        if (groupStatus != OemsStatus.OK && groupStatus != OemsStatus.DUPLICATE) return groupStatus;
        final int reservationSlot = groups.reservationSlot(groupSlot, groupGeneration);
        final int reservationGeneration = groups.reservationGeneration(groupSlot, groupGeneration);
        if (reservations.state(reservationSlot, reservationGeneration)
                == com.penguinsecure.basis.core.risk.RiskReservationState.RESERVED) {
            reservations.markSent(reservationSlot, reservationGeneration);
        }
        final RiskReservationStatus reservationStatus =
                reservations.markUnknown(reservationSlot, reservationGeneration);
        return reservationStatus == RiskReservationStatus.OK
                ? OemsStatus.OK
                : OemsStatus.INVALID_STATE;
    }

    public OemsStatus onInitiationFill(
            final AggressiveExecutionPlan plan,
            final int groupSlot,
            final int groupGeneration,
            final int childSlot,
            final int childGeneration,
            final long executionIdentityHash,
            final long fillQuantity,
            final long fillPriceTicks,
            final long nowMonoNanos,
            final MutableSlotHandle hedgeDestination) {
        if (plan == null
                || groups.strategyId(groupSlot, groupGeneration) != plan.strategyId()
                || groups.configurationGeneration(groupSlot, groupGeneration)
                        != plan.configurationGeneration()
                || !sameGroup(groupSlot, groupGeneration, childSlot, childGeneration)
                || children.role(childSlot, childGeneration) != ChildOrderRole.INITIATION) {
            return OemsStatus.CONFLICT;
        }
        final OemsStatus fillStatus =
                children.applyFill(
                        childSlot,
                        childGeneration,
                        executionIdentityHash,
                        fillQuantity,
                        fillPriceTicks);
        if (fillStatus != OemsStatus.OK) return fillStatus;
        final long cumulativeFill = children.filledQuantity(childSlot, childGeneration);
        groups.recordInitiationFill(groupSlot, groupGeneration, cumulativeFill);
        final int reservationSlot = groups.reservationSlot(groupSlot, groupGeneration);
        final int reservationGeneration = groups.reservationGeneration(groupSlot, groupGeneration);
        if (reservations.consumeFill(
                        reservationSlot,
                        reservationGeneration,
                        fillQuantity,
                        signed(plan.initiationSide(), fillQuantity))
                != RiskReservationStatus.OK) {
            groups.markUnknown(groupSlot, groupGeneration);
            return OemsStatus.NUMERIC_FAILURE;
        }
        if (CheckedDecimalMath.multiplyDivide(
                        cumulativeFill,
                        plan.hedgeQuantity(),
                        plan.initiationQuantity(),
                        RoundingPolicy.CEILING,
                        arithmeticScratch,
                        arithmeticResult)
                != NumericStatus.OK) {
            groups.markUnknown(groupSlot, groupGeneration);
            return OemsStatus.NUMERIC_FAILURE;
        }
        final long desiredHedge = arithmeticResult.value();
        final long hedgeIncrement =
                desiredHedge - groups.hedgeRequested(groupSlot, groupGeneration);
        if (hedgeIncrement <= 0) return OemsStatus.OK;
        if (!reservations.consumeHedgeClaim(reservationSlot, reservationGeneration)) {
            return attemptEmergencyUnwind(
                    plan, groupSlot, groupGeneration, fillQuantity, nowMonoNanos, hedgeDestination);
        }
        encodeNext(
                plan.cellId(),
                plan.hedgeVenueId(),
                plan.hedgeSessionGeneration(),
                plan.strategySlot());
        OemsStatus status =
                children.create(
                        localOrderId.high(),
                        localOrderId.low(),
                        groupSlot,
                        ChildOrderRole.HEDGE,
                        plan.hedgeVenueId(),
                        plan.hedgeInstrumentId(),
                        plan.hedgeSide(),
                        hedgeIncrement,
                        hedgeDestination);
        if (status != OemsStatus.OK) {
            reservations.refundHedgeClaim(reservationSlot, reservationGeneration);
            return attemptEmergencyUnwind(
                    plan, groupSlot, groupGeneration, fillQuantity, nowMonoNanos, hedgeDestination);
        }
        final boolean published =
                commands.tryPublish(
                        OrderCommandType.SUBMIT,
                        OrderUrgency.URGENT,
                        localOrderId.high(),
                        localOrderId.low(),
                        plan.hedgeVenueId(),
                        plan.hedgeInstrumentId(),
                        plan.hedgeSide(),
                        hedgeIncrement,
                        plan.hedgeLimitPriceTicks(),
                        nowMonoNanos);
        if (!published) {
            children.abandonCreated(hedgeDestination.slot(), hedgeDestination.generation());
            reservations.refundHedgeClaim(reservationSlot, reservationGeneration);
            return attemptEmergencyUnwind(
                    plan, groupSlot, groupGeneration, fillQuantity, nowMonoNanos, hedgeDestination);
        }
        children.markSendPending(hedgeDestination.slot(), hedgeDestination.generation());
        groups.recordHedgeRequested(groupSlot, groupGeneration, desiredHedge);
        return OemsStatus.OK;
    }

    public OemsStatus onHedgeWritten(final int childSlot, final int childGeneration) {
        return children.markSent(childSlot, childGeneration);
    }

    public OemsStatus onHedgeFill(
            final int groupSlot,
            final int groupGeneration,
            final int childSlot,
            final int childGeneration,
            final long executionIdentityHash,
            final long fillQuantity,
            final long fillPriceTicks) {
        if (!sameGroup(groupSlot, groupGeneration, childSlot, childGeneration)
                || children.role(childSlot, childGeneration) != ChildOrderRole.HEDGE) {
            return OemsStatus.CONFLICT;
        }
        final OemsStatus fillStatus =
                children.applyFill(
                        childSlot,
                        childGeneration,
                        executionIdentityHash,
                        fillQuantity,
                        fillPriceTicks);
        if (fillStatus != OemsStatus.OK) return fillStatus;
        final long cumulative = groups.hedgeFilled(groupSlot, groupGeneration) + fillQuantity;
        final OemsStatus groupStatus =
                groups.recordHedgeFill(groupSlot, groupGeneration, cumulative);
        if (groupStatus != OemsStatus.OK) return groupStatus;
        if (groups.state(groupSlot, groupGeneration) == ExecutionGroupState.BALANCED) {
            final int reservationSlot = groups.reservationSlot(groupSlot, groupGeneration);
            final int reservationGeneration =
                    groups.reservationGeneration(groupSlot, groupGeneration);
            reservations.releaseAuthoritatively(reservationSlot, reservationGeneration);
            groups.completeBalanced(groupSlot, groupGeneration);
        }
        return OemsStatus.OK;
    }

    private OemsStatus attemptEmergencyUnwind(
            final AggressiveExecutionPlan plan,
            final int groupSlot,
            final int groupGeneration,
            final long quantity,
            final long nowMonoNanos,
            final MutableSlotHandle destination) {
        final int reservationSlot = groups.reservationSlot(groupSlot, groupGeneration);
        final int reservationGeneration = groups.reservationGeneration(groupSlot, groupGeneration);
        if (!reservations.consumeEmergencyClaim(
                reservationSlot, reservationGeneration, nowMonoNanos)) {
            return markUnknown(groupSlot, groupGeneration);
        }
        encodeNext(
                plan.cellId(),
                plan.initiationVenueId(),
                plan.initiationSessionGeneration(),
                plan.strategySlot());
        final OemsStatus status =
                children.create(
                        localOrderId.high(),
                        localOrderId.low(),
                        groupSlot,
                        ChildOrderRole.UNWIND,
                        plan.initiationVenueId(),
                        plan.initiationInstrumentId(),
                        opposite(plan.initiationSide()),
                        quantity,
                        destination);
        if (status != OemsStatus.OK) {
            reservations.refundEmergencyClaim(reservationSlot, reservationGeneration);
            return markUnknown(groupSlot, groupGeneration);
        }
        if (!commands.tryPublish(
                OrderCommandType.SUBMIT,
                OrderUrgency.URGENT,
                localOrderId.high(),
                localOrderId.low(),
                plan.initiationVenueId(),
                plan.initiationInstrumentId(),
                opposite(plan.initiationSide()),
                quantity,
                plan.initiationLimitPriceTicks(),
                nowMonoNanos)) {
            children.abandonCreated(destination.slot(), destination.generation());
            reservations.refundEmergencyClaim(reservationSlot, reservationGeneration);
            return markUnknown(groupSlot, groupGeneration);
        }
        children.markSendPending(destination.slot(), destination.generation());
        groups.markUnwinding(groupSlot, groupGeneration);
        return OemsStatus.OK;
    }

    private OemsStatus markUnknown(final int groupSlot, final int groupGeneration) {
        groups.markUnknown(groupSlot, groupGeneration);
        final int slot = groups.reservationSlot(groupSlot, groupGeneration);
        final int generation = groups.reservationGeneration(groupSlot, groupGeneration);
        reservations.markUnknown(slot, generation);
        return OemsStatus.CAPACITY_EXHAUSTED;
    }

    private boolean sameGroup(
            final int groupSlot,
            final int groupGeneration,
            final int childSlot,
            final int childGeneration) {
        return groups.state(groupSlot, groupGeneration) != ExecutionGroupState.FREE
                && children.groupSlot(childSlot, childGeneration) == groupSlot;
    }

    private void encodeNext(
            final int cellId,
            final int venueId,
            final long sessionGeneration,
            final int strategySlot) {
        if (nextOrderSequence > LocalOrderIdCodec.MAX_SEQUENCE) {
            throw new IllegalStateException("local order sequence exhausted");
        }
        LocalOrderIdCodec.encode(
                cellId,
                venueId,
                sessionGeneration,
                strategySlot,
                nextOrderSequence++,
                localOrderId);
    }

    private static long signed(final OrderSide side, final long quantity) {
        return side == OrderSide.BUY ? quantity : -quantity;
    }

    private static OrderSide opposite(final OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }
}
