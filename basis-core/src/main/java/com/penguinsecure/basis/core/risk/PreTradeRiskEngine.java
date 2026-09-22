package com.penguinsecure.basis.core.risk;

import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;

/** Allocation-free ordered pre-trade checks followed by an atomic capacity reservation. */
public final class PreTradeRiskEngine {
    private final KillHierarchy kills;
    private final StrategyRiskLedger ledger;
    private final RiskReservationTable reservations;
    private final MutableLongResult arithmetic = new MutableLongResult();

    public PreTradeRiskEngine(
            final KillHierarchy kills,
            final StrategyRiskLedger ledger,
            final RiskReservationTable reservations) {
        if (kills == null || ledger == null || reservations == null) {
            throw new IllegalArgumentException("risk dependencies are required");
        }
        this.kills = kills;
        this.ledger = ledger;
        this.reservations = reservations;
    }

    public void evaluate(
            final PreTradeRiskRequest request,
            final RiskEnvelope envelope,
            final MutableRiskDecision destination) {
        if (request == null
                || envelope == null
                || destination == null
                || request.rateCapacity == null
                || request.hedgePathHealth == null) {
            if (destination != null) destination.reject(RiskRejectReason.INVALID_ARGUMENT);
            return;
        }
        final boolean reducing = strictlyReducesRisk(request);
        RiskRejectReason reason = identityAndKill(request, envelope, reducing);
        if (reason == RiskRejectReason.NONE) reason = evidence(request);
        if (reason == RiskRejectReason.NONE) reason = nativeOrder(request, envelope);
        if (reason == RiskRejectReason.NONE && !reducing) reason = exposure(request, envelope);
        if (reason == RiskRejectReason.NONE
                && !reducing
                && request.hedgeLiquidity < request.requestedUnhedgedExposure) {
            reason = RiskRejectReason.HEDGE_LIQUIDITY;
        }
        if (reason == RiskRejectReason.NONE
                && !reducing
                && request.requestedMaximumImbalance > envelope.maximumImbalance()) {
            reason = RiskRejectReason.IMBALANCE_LIMIT;
        }
        if (reason == RiskRejectReason.NONE && request.duplicateOrRunaway) {
            reason = RiskRejectReason.DUPLICATE_OR_RUNAWAY;
        }
        if (reason == RiskRejectReason.NONE) {
            final boolean rateAvailable =
                    reducing
                            ? request.rateCapacity.tryConsumeEmergency(request.nowMonoNanos)
                            : request.rateCapacity.reserveInitiation(
                                    request.hedgeRateClaims, request.nowMonoNanos);
            if (!rateAvailable) reason = RiskRejectReason.RATE_CAPACITY;
        }
        if (reason == RiskRejectReason.NONE
                && !reducing
                && !request.hedgePathHealth.permitsInitiation()) {
            request.rateCapacity.cancelInitiationReservation(request.hedgeRateClaims);
            reason = RiskRejectReason.HEDGE_PATH_UNHEALTHY;
        }
        if (reason != RiskRejectReason.NONE) {
            destination.reject(reason);
            return;
        }
        final long claims = reducing ? 0 : request.hedgeRateClaims;
        final RiskReservationStatus status =
                reservations.reserve(
                        request.strategySlot,
                        request.configurationGeneration,
                        request.requestedGrossExposure,
                        request.requestedNetExposure,
                        request.requestedUnhedgedExposure,
                        request.requestedCollateral,
                        claims,
                        request.opportunityExpiryMonoNanos,
                        request.rateCapacity,
                        destination.reservation());
        if (status != RiskReservationStatus.OK) {
            if (claims > 0) request.rateCapacity.cancelInitiationReservation(claims);
            else request.rateCapacity.refundEmergency();
            destination.reject(
                    status == RiskReservationStatus.CAPACITY_EXHAUSTED
                            ? RiskRejectReason.RESERVATION_CAPACITY
                            : RiskRejectReason.NUMERIC_FAILURE);
            return;
        }
        destination.approve(
                destination.reservation().slot(), destination.reservation().generation());
    }

    private RiskRejectReason identityAndKill(
            final PreTradeRiskRequest request,
            final RiskEnvelope envelope,
            final boolean reducing) {
        if (!addressable(request)) return RiskRejectReason.INVALID_ARGUMENT;
        if (!reducing
                && (kills.isKilled(KillScope.GLOBAL, 0)
                        || kills.isKilled(KillScope.STRATEGY, request.strategyId)
                        || kills.isKilled(KillScope.VENUE, request.firstVenueId)
                        || kills.isKilled(KillScope.VENUE, request.secondVenueId)
                        || kills.isKilled(KillScope.ACCOUNT, request.firstAccountId)
                        || kills.isKilled(KillScope.ACCOUNT, request.secondAccountId)
                        || kills.isKilled(KillScope.INSTRUMENT, request.firstInstrumentId)
                        || kills.isKilled(KillScope.INSTRUMENT, request.secondInstrumentId)
                        || kills.isKilled(KillScope.EXECUTION_GROUP, request.groupScopeId)
                        || kills.isKilled(KillScope.SESSION, request.sessionScopeId))) {
            return RiskRejectReason.KILLED;
        }
        if (request.riskReducing && !reducing) return RiskRejectReason.RISK_NOT_REDUCING;
        if (!ledger.matches(
                        request.strategySlot, request.strategyId, request.configurationGeneration)
                || envelope.strategyId() != request.strategyId
                || envelope.configurationGeneration() != request.configurationGeneration) {
            return RiskRejectReason.CONFIGURATION_GENERATION_MISMATCH;
        }
        if (envelope.envelopeGeneration() != request.envelopeGeneration) {
            return RiskRejectReason.ENVELOPE_GENERATION_MISMATCH;
        }
        if (!request.sessionsHealthy) return RiskRejectReason.SESSION_UNHEALTHY;
        if (request.expectedFirstSessionGeneration != request.currentFirstSessionGeneration
                || request.expectedSecondSessionGeneration
                        != request.currentSecondSessionGeneration) {
            return RiskRejectReason.SESSION_GENERATION_MISMATCH;
        }
        if (request.nowMonoNanos >= envelope.expiryMonoNanos())
            return RiskRejectReason.ENVELOPE_EXPIRED;
        return RiskRejectReason.NONE;
    }

    private boolean addressable(final PreTradeRiskRequest request) {
        return kills.canAddress(KillScope.GLOBAL, 0)
                && kills.canAddress(KillScope.STRATEGY, request.strategyId)
                && kills.canAddress(KillScope.VENUE, request.firstVenueId)
                && kills.canAddress(KillScope.VENUE, request.secondVenueId)
                && kills.canAddress(KillScope.ACCOUNT, request.firstAccountId)
                && kills.canAddress(KillScope.ACCOUNT, request.secondAccountId)
                && kills.canAddress(KillScope.INSTRUMENT, request.firstInstrumentId)
                && kills.canAddress(KillScope.INSTRUMENT, request.secondInstrumentId)
                && kills.canAddress(KillScope.EXECUTION_GROUP, request.groupScopeId)
                && kills.canAddress(KillScope.SESSION, request.sessionScopeId);
    }

    private static RiskRejectReason evidence(final PreTradeRiskRequest request) {
        if (request.nowMonoNanos >= request.opportunityExpiryMonoNanos) {
            return RiskRejectReason.OPPORTUNITY_EXPIRED;
        }
        if (!request.currentFirstBookTrusted || !request.currentSecondBookTrusted) {
            return RiskRejectReason.BOOK_UNTRUSTED;
        }
        if (request.firstBookEpoch != request.currentFirstBookEpoch
                || request.firstBookSequence != request.currentFirstBookSequence
                || request.secondBookEpoch != request.currentSecondBookEpoch
                || request.secondBookSequence != request.currentSecondBookSequence
                || request.firstReceiveMonoNanos != request.currentFirstReceiveMonoNanos
                || request.secondReceiveMonoNanos != request.currentSecondReceiveMonoNanos) {
            return RiskRejectReason.BOOK_EVIDENCE_CHANGED;
        }
        if (age(request.nowMonoNanos, request.currentFirstReceiveMonoNanos)
                        > request.firstMaximumAgeNanos
                || age(request.nowMonoNanos, request.currentSecondReceiveMonoNanos)
                        > request.secondMaximumAgeNanos) return RiskRejectReason.BOOK_STALE;
        if (absoluteDifference(
                        request.currentFirstReceiveMonoNanos, request.currentSecondReceiveMonoNanos)
                > request.maximumSkewNanos) {
            return RiskRejectReason.BOOK_SKEW;
        }
        return RiskRejectReason.NONE;
    }

    private static RiskRejectReason nativeOrder(
            final PreTradeRiskRequest request, final RiskEnvelope envelope) {
        if (!validQuantity(
                        request.firstNativeQuantity,
                        request.firstLotSize,
                        request.firstMinimumQuantity,
                        request.firstMaximumQuantity)
                || !validQuantity(
                        request.secondNativeQuantity,
                        request.secondLotSize,
                        request.secondMinimumQuantity,
                        request.secondMaximumQuantity)) {
            return RiskRejectReason.NATIVE_QUANTITY_INVALID;
        }
        if (!validPrice(request.firstWorstPriceTicks, request.firstTickSize)
                || !validPrice(request.secondWorstPriceTicks, request.secondTickSize)) {
            return RiskRejectReason.PRICE_INVALID;
        }
        if (absoluteDifference(request.firstWorstPriceTicks, request.firstReferencePriceTicks)
                        > envelope.maximumPriceDeviationTicks()
                || absoluteDifference(
                                request.secondWorstPriceTicks, request.secondReferencePriceTicks)
                        > envelope.maximumPriceDeviationTicks())
            return RiskRejectReason.PRICE_BAND_EXCEEDED;
        return RiskRejectReason.NONE;
    }

    private RiskRejectReason exposure(
            final PreTradeRiskRequest request, final RiskEnvelope envelope) {
        if (ledger.activeGroups(request.strategySlot) >= envelope.maximumConcurrentGroups()) {
            return RiskRejectReason.GROUP_LIMIT;
        }
        if (sumExceeds(
                ledger.confirmedGross(request.strategySlot),
                ledger.pendingGross(request.strategySlot),
                request.requestedGrossExposure,
                envelope.maximumGrossExposure())) return RiskRejectReason.GROSS_LIMIT;
        if (absoluteSumExceeds(
                ledger.netExposure(request.strategySlot),
                ledger.pendingNetExposure(request.strategySlot),
                request.requestedNetExposure,
                envelope.maximumNetExposure())) {
            return RiskRejectReason.NET_LIMIT;
        }
        if (sumExceeds(
                ledger.pendingUnhedgedExposure(request.strategySlot),
                0,
                request.requestedUnhedgedExposure,
                envelope.maximumUnhedgedExposure())) {
            return RiskRejectReason.UNHEDGED_LIMIT;
        }
        if (absoluteSumExceeds(
                ledger.position(request.strategySlot),
                ledger.pendingNetExposure(request.strategySlot),
                request.requestedNetExposure,
                envelope.maximumPosition())) return RiskRejectReason.POSITION_LIMIT;
        if (sumExceeds(
                ledger.reservedCollateral(request.strategySlot),
                0,
                request.requestedCollateral,
                envelope.maximumCollateral())) {
            return RiskRejectReason.COLLATERAL_LIMIT;
        }
        if (ledger.dailyLoss(request.strategySlot) >= envelope.maximumDailyLoss()) {
            return RiskRejectReason.DAILY_LOSS_LIMIT;
        }
        return RiskRejectReason.NONE;
    }

    private boolean strictlyReducesRisk(final PreTradeRiskRequest request) {
        if (!request.riskReducing
                || request.worstExposureAfter < 0
                || request.worstExposureAfter >= request.worstExposureBefore) return false;
        if (CheckedDecimalMath.add(
                        ledger.netExposure(request.strategySlot),
                        ledger.pendingNetExposure(request.strategySlot),
                        arithmetic)
                != NumericStatus.OK) return false;
        final long currentNet = arithmetic.value();
        if (absolute(currentNet) == 0
                || request.worstExposureBefore < absolute(currentNet)
                || CheckedDecimalMath.add(currentNet, request.requestedNetExposure, arithmetic)
                        != NumericStatus.OK) return false;
        return absolute(arithmetic.value()) < absolute(currentNet)
                && request.worstExposureAfter >= absolute(arithmetic.value());
    }

    private boolean sumExceeds(
            final long first, final long second, final long third, final long limit) {
        return CheckedDecimalMath.add(first, second, arithmetic) != NumericStatus.OK
                || CheckedDecimalMath.add(arithmetic.value(), third, arithmetic) != NumericStatus.OK
                || arithmetic.value() > limit;
    }

    private boolean absoluteSumExceeds(
            final long first, final long second, final long third, final long limit) {
        return CheckedDecimalMath.add(first, second, arithmetic) != NumericStatus.OK
                || CheckedDecimalMath.add(arithmetic.value(), third, arithmetic) != NumericStatus.OK
                || absolute(arithmetic.value()) > limit;
    }

    private static boolean validQuantity(
            final long quantity, final long lot, final long minimum, final long maximum) {
        return quantity > 0
                && lot > 0
                && quantity % lot == 0
                && quantity >= minimum
                && quantity <= maximum;
    }

    private static boolean validPrice(final long price, final long tick) {
        return price > 0 && tick > 0 && price % tick == 0;
    }

    private static long age(final long now, final long then) {
        return now < then ? Long.MAX_VALUE : now - then;
    }

    private static long absoluteDifference(final long first, final long second) {
        if ((first >= 0 && second < 0) || (first < 0 && second >= 0)) {
            return Long.MAX_VALUE;
        }
        return absolute(first - second);
    }

    private static long absolute(final long value) {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
    }
}
