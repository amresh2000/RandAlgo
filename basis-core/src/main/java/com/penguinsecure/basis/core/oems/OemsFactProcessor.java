package com.penguinsecure.basis.core.oems;

import com.penguinsecure.basis.core.oems.fact.MutableOrderFact;
import com.penguinsecure.basis.core.oems.fact.OrderFactProvenance;

/** Single-writer router from normalized venue facts into generation-fenced OEMS state. */
public final class OemsFactProcessor {
    private final AggressiveExecutionEngine engine;
    private final ExecutionGroupTable groups;
    private final ChildOrderTable children;
    private final MutableSlotHandle resolvedChild = new MutableSlotHandle();
    private final MutableSlotHandle hedgeDestination = new MutableSlotHandle();

    public OemsFactProcessor(
            final AggressiveExecutionEngine engine,
            final ExecutionGroupTable groups,
            final ChildOrderTable children) {
        if (engine == null || groups == null || children == null) {
            throw new IllegalArgumentException("dependencies are required");
        }
        this.engine = engine;
        this.groups = groups;
        this.children = children;
    }

    public OemsStatus process(
            final MutableOrderFact fact, final AggressiveExecutionPlan activePlan) {
        if (fact == null || !fact.isComplete()) return OemsStatus.INVALID_STATE;
        if (fact.provenance() == OrderFactProvenance.COUNTERFACTUAL) return OemsStatus.OK;
        if (!identityMatches(fact)
                || children.resolve(
                                fact.localOrderIdHigh(),
                                fact.localOrderIdLow(),
                                resolvedChild.clear())
                        != OemsStatus.OK) return OemsStatus.STALE_HANDLE;
        final int childSlot = resolvedChild.slot();
        final int childGeneration = resolvedChild.generation();
        if (children.venueId(childSlot, childGeneration) != fact.venueId()
                || children.instrumentId(childSlot, childGeneration) != fact.instrumentId()) {
            return OemsStatus.CONFLICT;
        }
        final int groupSlot = children.groupSlot(childSlot, childGeneration);
        final int groupGeneration = groups.generationAt(groupSlot);
        if (groupGeneration == 0
                || groups.state(groupSlot, groupGeneration) == ExecutionGroupState.FREE) {
            return OemsStatus.STALE_HANDLE;
        }

        return switch (fact.type()) {
            case WRITE_ACCEPTED ->
                    children.role(childSlot, childGeneration) == ChildOrderRole.INITIATION
                            ? engine.onInitiationWritten(
                                    groupSlot, groupGeneration, childSlot, childGeneration)
                            : engine.onHedgeWritten(childSlot, childGeneration);
            case WRITE_FAILED, RATE_LIMITED ->
                    engine.onWriteFailed(groupSlot, groupGeneration, childSlot, childGeneration);
            case WRITE_AMBIGUOUS, DISCONNECTED ->
                    engine.onWriteAmbiguous(groupSlot, groupGeneration, childSlot, childGeneration);
            case ACKNOWLEDGED -> children.acknowledge(childSlot, childGeneration);
            case WORKING -> children.markWorking(childSlot, childGeneration);
            case FILL ->
                    applyFill(
                            fact,
                            activePlan,
                            groupSlot,
                            groupGeneration,
                            childSlot,
                            childGeneration);
            case CANCELLED ->
                    engine.onCancelled(groupSlot, groupGeneration, childSlot, childGeneration);
            case REJECTED ->
                    engine.onRejected(groupSlot, groupGeneration, childSlot, childGeneration);
            case RECONCILED -> reconcile(fact, childSlot, childGeneration);
        };
    }

    private OemsStatus applyFill(
            final MutableOrderFact fact,
            final AggressiveExecutionPlan activePlan,
            final int groupSlot,
            final int groupGeneration,
            final int childSlot,
            final int childGeneration) {
        final ChildOrderRole role = children.role(childSlot, childGeneration);
        if (role == ChildOrderRole.INITIATION) {
            if (activePlan == null) return OemsStatus.INVALID_STATE;
            return engine.onInitiationFill(
                    activePlan,
                    groupSlot,
                    groupGeneration,
                    childSlot,
                    childGeneration,
                    fact.executionIdentityHash(),
                    fact.fillQuantity(),
                    fact.fillPriceTicks(),
                    fact.receiveMonoNanos(),
                    hedgeDestination.clear());
        }
        if (role == ChildOrderRole.HEDGE) {
            return engine.onHedgeFill(
                    groupSlot,
                    groupGeneration,
                    childSlot,
                    childGeneration,
                    fact.executionIdentityHash(),
                    fact.fillQuantity(),
                    fact.fillPriceTicks());
        }
        return children.applyFill(
                childSlot,
                childGeneration,
                fact.executionIdentityHash(),
                fact.fillQuantity(),
                fact.fillPriceTicks());
    }

    private OemsStatus reconcile(
            final MutableOrderFact fact, final int childSlot, final int childGeneration) {
        final ChildOrderState state = children.state(childSlot, childGeneration);
        if (state == ChildOrderState.UNKNOWN) {
            final OemsStatus status = children.beginReconciliation(childSlot, childGeneration);
            if (status != OemsStatus.OK) return status;
        }
        return children.reconcile(
                childSlot, childGeneration, fact.authoritativeFilled(), fact.authoritativeState());
    }

    private static boolean identityMatches(final MutableOrderFact fact) {
        final int encodedVenue = (int) ((fact.localOrderIdHigh() >>> 32) & 0xFFFFL);
        final long encodedSession = fact.localOrderIdHigh() & 0xFFFF_FFFFL;
        return encodedVenue == fact.venueId() && encodedSession == fact.sessionGeneration();
    }
}
