package com.penguinsecure.basis.venue.bybit.order;

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

/** Single-owner Bybit command serializer/correlator. Trade responses never create fills. */
public final class BybitOrderAgent implements OrderCommandHandler {
    private final BybitOrderProfile profile;
    private final BybitAuthenticatedSession session;
    private final VenueConnectionControl connection;
    private final BybitOrderRequestEncoder encoder;
    private final BybitRequestCorrelationTable correlations;
    private final BybitActiveOrderTable activeOrders;
    private final BybitRateLimitState rateLimits;
    private final VenueOrderFactSink facts;
    private final EpochClock epochClock;
    private final MonotonicClock monotonicClock;
    private final MutableVenueOrderFact fact = new MutableVenueOrderFact();
    private long commands;
    private long rejectedCommands;

    @SuppressWarnings("ParameterNumber")
    public BybitOrderAgent(
            final BybitOrderProfile profile,
            final BybitAuthenticatedSession session,
            final VenueConnectionControl connection,
            final BybitOrderRequestEncoder encoder,
            final BybitRequestCorrelationTable correlations,
            final BybitActiveOrderTable activeOrders,
            final BybitRateLimitState rateLimits,
            final VenueOrderFactSink facts,
            final EpochClock epochClock,
            final MonotonicClock monotonicClock) {
        if (profile == null
                || session == null
                || connection == null
                || encoder == null
                || correlations == null
                || activeOrders == null
                || rateLimits == null
                || facts == null
                || epochClock == null
                || monotonicClock == null)
            throw new NullPointerException("agent dependencies are required");
        this.profile = profile;
        this.session = session;
        this.connection = connection;
        this.encoder = encoder;
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
        if (session.state() != VenueSessionState.LIVE
                || generation != session.sessionGeneration()
                || command.venueId() != profile.venueId()
                || command.instrumentId() != profile.instrumentId()
                || (!submit
                        && activeOrders.find(command.localOrderIdHigh(), command.localOrderIdLow())
                                < 0)
                || !correlations.register(
                        command.localOrderIdHigh(),
                        command.localOrderIdLow(),
                        generation,
                        command.type())) {
            rejectedCommands++;
            publish(
                    command.localOrderIdHigh(),
                    command.localOrderIdLow(),
                    generation,
                    VenueOrderFactType.WRITE_FAILED,
                    0);
            return;
        }
        if (submit
                && !activeOrders.register(
                        command.localOrderIdHigh(),
                        command.localOrderIdLow(),
                        command.quantity())) {
            correlations.complete(
                    command.localOrderIdHigh(), command.localOrderIdLow(), generation);
            rejectedCommands++;
            publish(
                    command.localOrderIdHigh(),
                    command.localOrderIdLow(),
                    generation,
                    VenueOrderFactType.WRITE_FAILED,
                    0);
            return;
        }
        try {
            connection.sendText(encoder.command(command, epochClock.epochNanos() / 1_000_000L));
            if (command.type() == OrderCommandType.SUBMIT) {
                publish(
                        command.localOrderIdHigh(),
                        command.localOrderIdLow(),
                        generation,
                        VenueOrderFactType.WRITE_ACCEPTED,
                        0);
            }
        } catch (RuntimeException exception) {
            correlations.complete(
                    command.localOrderIdHigh(), command.localOrderIdLow(), generation);
            publish(
                    command.localOrderIdHigh(),
                    command.localOrderIdLow(),
                    generation,
                    VenueOrderFactType.WRITE_AMBIGUOUS,
                    0);
        }
    }

    public BybitOrderParseStatus onResponse(final MutableBybitTradeResponse response) {
        if (response == null) return BybitOrderParseStatus.MALFORMED;
        if (response.rateLimit() > 0) {
            rateLimits.update(
                    response.rateLimit(), response.rateRemaining(), response.rateResetMillis());
        }
        if (response.kind() == BybitTradeResponseKind.PONG
                || response.kind() == BybitTradeResponseKind.AUTHENTICATED
                || response.kind() == BybitTradeResponseKind.AUTHENTICATION_FAILED) {
            return BybitOrderParseStatus.IGNORED;
        }
        final int index =
                correlations.find(response.localOrderIdHigh(), response.localOrderIdLow());
        final long encodedGeneration = response.localOrderIdHigh() & 0xffff_ffffL;
        final int encodedVenue = (int) ((response.localOrderIdHigh() >>> 32) & 0xffffL);
        if (index < 0) {
            return encodedGeneration == session.sessionGeneration()
                            && encodedVenue == profile.venueId()
                    ? BybitOrderParseStatus.IGNORED
                    : BybitOrderParseStatus.INVALID_IDENTITY;
        }
        if (correlations.sessionGeneration(index) != session.sessionGeneration()) {
            return BybitOrderParseStatus.INVALID_IDENTITY;
        }
        final OrderCommandType commandType = correlations.type(index);
        correlations.remove(index);
        if (response.kind() == BybitTradeResponseKind.COMMAND_REJECTED
                || response.kind() == BybitTradeResponseKind.RATE_LIMITED) {
            if (commandType == OrderCommandType.SUBMIT) {
                activeOrders.remove(response.localOrderIdHigh(), response.localOrderIdLow());
            }
            publish(
                    response.localOrderIdHigh(),
                    response.localOrderIdLow(),
                    session.sessionGeneration(),
                    commandType == OrderCommandType.SUBMIT
                            ? response.kind() == BybitTradeResponseKind.RATE_LIMITED
                                    ? VenueOrderFactType.RATE_LIMITED
                                    : VenueOrderFactType.REJECTED
                            : VenueOrderFactType.WRITE_AMBIGUOUS,
                    response.returnCode());
        }
        return BybitOrderParseStatus.OK;
    }

    public int onDisconnected() {
        int ambiguous = 0;
        for (int index = 0; index < correlations.capacity(); index++) {
            if (!correlations.active(index)) continue;
            if (correlations.type(index) == OrderCommandType.SUBMIT) {
                activeOrders.remove(correlations.idHigh(index), correlations.idLow(index));
            }
            publish(
                    correlations.idHigh(index),
                    correlations.idLow(index),
                    correlations.sessionGeneration(index),
                    VenueOrderFactType.WRITE_AMBIGUOUS,
                    0);
            correlations.remove(index);
            ambiguous++;
        }
        return ambiguous;
    }

    /** Marks every nonterminal locally owned order ambiguous after private truth is lost. */
    public int onPrivateDisconnected() {
        int ambiguous = 0;
        for (int index = 0; index < activeOrders.capacity(); index++) {
            if (!activeOrders.active(index)) continue;
            final long high = activeOrders.idHigh(index);
            publish(
                    high,
                    activeOrders.idLow(index),
                    high & 0xffff_ffffL,
                    VenueOrderFactType.DISCONNECTED,
                    0);
            activeOrders.remove(index);
            ambiguous++;
        }
        return ambiguous;
    }

    public long commands() {
        return commands;
    }

    public long rejectedCommands() {
        return rejectedCommands;
    }

    private void publish(
            final long idHigh,
            final long idLow,
            final long generation,
            final VenueOrderFactType type,
            final int reasonCode) {
        final long mono = monotonicClock.nanoTime();
        fact.set(
                type,
                idHigh,
                idLow,
                profile.venueId(),
                profile.instrumentId(),
                generation,
                epochClock.epochNanos(),
                mono,
                0,
                0,
                0,
                0,
                null,
                reasonCode);
        facts.publish(fact);
    }
}
