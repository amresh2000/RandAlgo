package com.penguinsecure.basis.core.recovery;

import com.penguinsecure.basis.core.oems.ChildOrderState;
import com.penguinsecure.basis.core.oems.ExecutionGroupState;
import com.penguinsecure.basis.core.risk.RiskReservationState;

/** Cross-table validation performed before a recovered aggregate can be published. */
public final class CoreStateRestorer {
    private CoreStateRestorer() {}

    public static RecoveryValidationStatus validate(final CoreRecoveryState state) {
        if (state == null) return RecoveryValidationStatus.INVALID_CAPACITY;
        final int strategies = state.ledger().capacity();
        final long[] gross = new long[strategies];
        final long[] net = new long[strategies];
        final long[] unhedged = new long[strategies];
        final long[] collateral = new long[strategies];
        final int[] reservationCounts = new int[strategies];
        for (int slot = 0; slot < state.reservations().capacity(); slot++) {
            if (state.reservations().stateAt(slot) == RiskReservationState.FREE) continue;
            final int generation = state.reservations().generationAt(slot);
            if (generation <= 0) return RecoveryValidationStatus.INVALID_GENERATION;
            final int strategy = state.reservations().strategySlot(slot, generation);
            if (strategy < 0
                    || strategy >= strategies
                    || !state.ledger()
                            .matchesGeneration(
                                    strategy,
                                    state.reservations()
                                            .configurationGeneration(slot, generation))) {
                return RecoveryValidationStatus.INVALID_REFERENCE;
            }
            try {
                gross[strategy] =
                        Math.addExact(
                                gross[strategy],
                                state.reservations().remainingGross(slot, generation));
                net[strategy] =
                        Math.addExact(
                                net[strategy],
                                state.reservations().reservedNetExposure(slot, generation));
                unhedged[strategy] =
                        Math.addExact(
                                unhedged[strategy],
                                state.reservations().reservedUnhedgedExposure(slot, generation));
                collateral[strategy] =
                        Math.addExact(
                                collateral[strategy],
                                state.reservations().collateral(slot, generation));
                reservationCounts[strategy] = Math.addExact(reservationCounts[strategy], 1);
            } catch (ArithmeticException exception) {
                return RecoveryValidationStatus.INVALID_ARITHMETIC;
            }
        }
        for (int strategy = 0; strategy < strategies; strategy++) {
            if (state.ledger().strategyId(strategy) == 0) continue;
            if (gross[strategy] != state.ledger().pendingGross(strategy)
                    || net[strategy] != state.ledger().pendingNetExposure(strategy)
                    || unhedged[strategy] != state.ledger().pendingUnhedgedExposure(strategy)
                    || collateral[strategy] != state.ledger().reservedCollateral(strategy)
                    || reservationCounts[strategy] != state.ledger().activeGroups(strategy)) {
                return RecoveryValidationStatus.INVALID_ARITHMETIC;
            }
        }
        for (int slot = 0; slot < state.groups().capacity(); slot++) {
            final int generation = state.groups().generationAt(slot);
            if (state.groups().stateAt(slot) == ExecutionGroupState.FREE) continue;
            if (generation <= 0) return RecoveryValidationStatus.INVALID_GENERATION;
            final int reservationSlot = state.groups().reservationSlot(slot, generation);
            final int reservationGeneration =
                    state.groups().reservationGeneration(slot, generation);
            if (reservationSlot < 0
                    || reservationSlot >= state.reservations().capacity()
                    || state.reservations().generationAt(reservationSlot) != reservationGeneration
                    || state.reservations().stateAt(reservationSlot) == RiskReservationState.FREE) {
                return RecoveryValidationStatus.INVALID_REFERENCE;
            }
        }
        for (int slot = 0; slot < state.children().capacity(); slot++) {
            final int generation = state.children().generationAt(slot);
            if (state.children().stateAt(slot) == ChildOrderState.FREE) continue;
            if (generation <= 0) return RecoveryValidationStatus.INVALID_GENERATION;
            final int groupSlot = state.children().groupSlot(slot, generation);
            if (groupSlot < 0
                    || groupSlot >= state.groups().capacity()
                    || state.groups().stateAt(groupSlot) == ExecutionGroupState.FREE) {
                return RecoveryValidationStatus.INVALID_REFERENCE;
            }
            if (state.children().filledQuantity(slot, generation)
                            + state.children().possibleOutstanding(slot, generation)
                    > state.children().quantity(slot, generation)) {
                return RecoveryValidationStatus.INVALID_ARITHMETIC;
            }
        }
        return RecoveryValidationStatus.VALID;
    }
}
