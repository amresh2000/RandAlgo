package com.penguinsecure.basis.app.certification;

import java.util.EnumSet;
import java.util.Set;

/** Complete Phase 12 fault and execution matrix. */
public enum CertificationScenario {
    ACCEPTED(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    REJECTED(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    PARTIAL_FILL(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    FULL_FILL(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    IOC_RESIDUAL(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    CANCEL_RACE(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    DUPLICATE(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    LOST_ACK(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    PRIVATE_DISCONNECT(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    PUBLIC_GAP(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    TOKEN_EXPIRY(CertificationVenue.DERIBIT),
    RATE_LIMIT(CertificationVenue.BYBIT, CertificationVenue.DERIBIT),
    PROCESS_KILL(CertificationVenue.CELL),
    ARCHIVE_OUTAGE(CertificationVenue.CELL),
    RESTART(CertificationVenue.CELL),
    MANUAL_KILL(CertificationVenue.CELL),
    FRESH_BOOKS_HIGH_SKEW(CertificationVenue.CELL),
    URGENT_QUEUE_LATENCY(CertificationVenue.CELL),
    ORDER_AGENT_LATENCY(CertificationVenue.CELL);

    private final Set<CertificationVenue> venues;

    CertificationScenario(
            final CertificationVenue first, final CertificationVenue... additionalVenues) {
        final EnumSet<CertificationVenue> values = EnumSet.of(first);
        for (CertificationVenue venue : additionalVenues) values.add(venue);
        venues = Set.copyOf(values);
    }

    public Set<CertificationVenue> venues() {
        return venues;
    }

    public boolean appliesTo(final CertificationVenue venue) {
        return venues.contains(venue);
    }
}
