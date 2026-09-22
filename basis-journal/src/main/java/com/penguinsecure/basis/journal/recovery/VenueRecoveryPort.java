package com.penguinsecure.basis.journal.recovery;

/**
 * Future private venue adapters report bounded authoritative recovery evidence here.
 *
 * <p>An authoritative result may become {@code true} only after every bounded page was consumed,
 * local UNKNOWN state was resolved, and every difference was admitted as a compensating durable
 * fact. Merely completing an HTTP request is not authoritative.
 */
public interface VenueRecoveryPort {
    boolean privateConnected();

    boolean openOrdersAuthoritative();

    boolean positionsAndBalancesAuthoritative();
}
