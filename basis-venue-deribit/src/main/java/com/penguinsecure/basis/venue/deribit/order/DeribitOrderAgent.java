package com.penguinsecure.basis.venue.deribit.order;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandHandler;
import com.penguinsecure.basis.core.command.OrderCommandType;
import com.penguinsecure.basis.core.time.EpochClock;
import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.venue.api.order.MutableVenueOrderFact;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactSink;
import com.penguinsecure.basis.venue.api.order.VenueOrderFactType;
import com.penguinsecure.basis.venue.api.session.VenueConnectionControl;
import com.penguinsecure.basis.venue.api.session.VenueSessionState;

/** Single-owner Deribit JSON-RPC command serializer. Responses never create fills. */
public final class DeribitOrderAgent implements OrderCommandHandler {
    private static final int RATE_LIMITED = 10028;
    private static final int TOKEN_EXPIRED = 13009;
    private final DeribitOrderProfile profile;
    private final DeribitAuthenticatedSession session;
    private final VenueConnectionControl connection;
    private final DeribitOrderRequestEncoder encoder;
    private final DeribitRequestIdSequence requestIds;
    private final DeribitRequestCorrelationTable correlations;
    private final DeribitActiveOrderTable activeOrders;
    private final DeribitRateLimitState rateLimits;
    private final VenueOrderFactSink facts;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final MutableVenueOrderFact fact = new MutableVenueOrderFact();
    private final StringBuilder venueOrderId = new StringBuilder(128);
    private long commands, rejectedCommands;

    @SuppressWarnings("ParameterNumber")
    public DeribitOrderAgent(
            final DeribitOrderProfile profile,
            final DeribitAuthenticatedSession session,
            final VenueConnectionControl connection,
            final DeribitOrderRequestEncoder encoder,
            final DeribitRequestIdSequence requestIds,
            final DeribitRequestCorrelationTable correlations,
            final DeribitActiveOrderTable activeOrders,
            final DeribitRateLimitState rateLimits,
            final VenueOrderFactSink facts,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock) {
        if (profile == null
                || session == null
                || connection == null
                || encoder == null
                || requestIds == null
                || correlations == null
                || activeOrders == null
                || rateLimits == null
                || facts == null
                || epochClock == null
                || monotonicClock == null)
            throw new NullPointerException("dependencies are required");
        this.profile = profile;
        this.session = session;
        this.connection = connection;
        this.encoder = encoder;
        this.requestIds = requestIds;
        this.correlations = correlations;
        this.activeOrders = activeOrders;
        this.rateLimits = rateLimits;
        this.facts = facts;
        this.epochClock = epochClock;
        this.monotonicClock = monotonicClock;
    }

    @Override
    public void onCommand(final MutableOrderCommand command) {
        commands++;
        final long generation = command.localOrderIdHigh() & 0xffff_ffffL;
        final boolean submit = command.type() == OrderCommandType.SUBMIT;
        final int activeIndex =
                activeOrders.find(command.localOrderIdHigh(), command.localOrderIdLow());
        if (session.state() != VenueSessionState.LIVE
                || generation != session.sessionGeneration()
                || command.venueId() != profile.venueId()
                || command.instrumentId() != profile.instrumentId()
                || (!submit && (command.type() != OrderCommandType.CANCEL || activeIndex < 0))) {
            rejectWrite(command, generation);
            return;
        }
        final long requestId;
        try {
            requestId = requestIds.next();
        } catch (IllegalStateException exception) {
            rejectWrite(command, generation);
            return;
        }
        if (!correlations.register(
                requestId,
                command.localOrderIdHigh(),
                command.localOrderIdLow(),
                generation,
                command.type())) {
            rejectWrite(command, generation);
            return;
        }
        if (submit
                && !activeOrders.register(
                        command.localOrderIdHigh(),
                        command.localOrderIdLow(),
                        command.quantity())) {
            correlations.remove(correlations.find(requestId));
            rejectWrite(command, generation);
            return;
        }
        try {
            if (submit) connection.sendText(encoder.submit(command, requestId));
            else {
                venueOrderId.setLength(0);
                if (!activeOrders.appendVenueId(activeIndex, venueOrderId))
                    throw new IllegalStateException("venue order id unavailable");
                connection.sendText(encoder.cancel(requestId, venueOrderId));
            }
            if (submit)
                publish(
                        command.localOrderIdHigh(),
                        command.localOrderIdLow(),
                        generation,
                        VenueOrderFactType.WRITE_ACCEPTED,
                        0);
        } catch (RuntimeException exception) {
            correlations.remove(correlations.find(requestId));
            publish(
                    command.localOrderIdHigh(),
                    command.localOrderIdLow(),
                    generation,
                    VenueOrderFactType.WRITE_AMBIGUOUS,
                    0);
        } finally {
            venueOrderId.setLength(0);
        }
    }

    public DeribitOrderParseStatus onResponse(final MutableDeribitResponse response) {
        if (response == null || response.requestId() <= 0) return DeribitOrderParseStatus.MALFORMED;
        final int index = correlations.find(response.requestId());
        if (index < 0) return DeribitOrderParseStatus.IGNORED;
        if (correlations.generation(index) != session.sessionGeneration())
            return DeribitOrderParseStatus.INVALID_IDENTITY;
        final long high = correlations.idHigh(index),
                low = correlations.idLow(index),
                generation = correlations.generation(index);
        final OrderCommandType type = correlations.type(index);
        correlations.remove(index);
        if (response.errorCode() != 0) {
            if (type == OrderCommandType.SUBMIT && response.errorCode() != TOKEN_EXPIRED)
                activeOrders.remove(activeOrders.find(high, low));
            if (response.errorCode() == RATE_LIMITED) rateLimits.rejected(response.errorCode());
            final VenueOrderFactType factType =
                    response.errorCode() == TOKEN_EXPIRED
                            ? VenueOrderFactType.WRITE_AMBIGUOUS
                            : type == OrderCommandType.SUBMIT
                                    ? response.errorCode() == RATE_LIMITED
                                            ? VenueOrderFactType.RATE_LIMITED
                                            : VenueOrderFactType.REJECTED
                                    : VenueOrderFactType.WRITE_AMBIGUOUS;
            publish(high, low, generation, factType, response.errorCode());
            return DeribitOrderParseStatus.OK;
        }
        return DeribitOrderParseStatus.OK;
    }

    public int onDisconnected() {
        return resolveDisconnect(false);
    }

    public int onPrivateDisconnected() {
        return resolveDisconnect(true);
    }

    public long commands() {
        return commands;
    }

    public long rejectedCommands() {
        return rejectedCommands;
    }

    private int resolveDisconnect(final boolean privateTruth) {
        int count = 0;
        if (privateTruth) {
            for (int index = 0; index < activeOrders.capacity(); index++)
                if (activeOrders.active(index)) {
                    final long high = activeOrders.idHigh(index);
                    publish(
                            high,
                            activeOrders.idLow(index),
                            high & 0xffff_ffffL,
                            VenueOrderFactType.DISCONNECTED,
                            0);
                    activeOrders.remove(index);
                    count++;
                }
        } else {
            for (int index = 0; index < correlations.capacity(); index++)
                if (correlations.active(index)) {
                    publish(
                            correlations.idHigh(index),
                            correlations.idLow(index),
                            correlations.generation(index),
                            VenueOrderFactType.WRITE_AMBIGUOUS,
                            0);
                    correlations.remove(index);
                    count++;
                }
        }
        return count;
    }

    private void rejectWrite(final MutableOrderCommand command, final long generation) {
        rejectedCommands++;
        publish(
                command.localOrderIdHigh(),
                command.localOrderIdLow(),
                generation,
                VenueOrderFactType.WRITE_FAILED,
                0);
    }

    private void publish(
            final long high,
            final long low,
            final long generation,
            final VenueOrderFactType type,
            final int reason) {
        fact.set(
                type,
                high,
                low,
                profile.venueId(),
                profile.instrumentId(),
                generation,
                epochClock.epochNanos(),
                monotonicClock.nanoTime(),
                0,
                0,
                0,
                0,
                null,
                reason);
        facts.publish(fact);
    }
}
