package com.penguinsecure.basis.journal.recovery;

import com.penguinsecure.basis.core.recovery.CoreRecoveryState;

/** Evidence-gated, fail-closed restart coordinator. Maximum success is DISARMED_READY. */
public final class RecoveryCoordinator {
    private final VenueRecoveryPort[] venues;
    private final BookRecoveryPort[] books;
    private final RecoveryInvariantChecker invariantChecker;
    private RecoveryStage stage = RecoveryStage.BOOT;

    public RecoveryCoordinator(
            final VenueRecoveryPort[] venues,
            final BookRecoveryPort[] books,
            final RecoveryInvariantChecker invariantChecker) {
        if (venues == null
                || venues.length < 2
                || books == null
                || books.length < 2
                || invariantChecker == null) {
            throw new IllegalArgumentException("both venue and book recovery ports are required");
        }
        this.venues = venues.clone();
        this.books = books.clone();
        this.invariantChecker = invariantChecker;
    }

    public RecoveryStatus snapshotLoaded(
            final CoreRecoveryState state, final boolean snapshotVerified) {
        if (stage != RecoveryStage.BOOT) return RecoveryStatus.INVALID_PREDECESSOR;
        if (!snapshotVerified || !invariantChecker.valid(state)) return fail();
        stage = RecoveryStage.SNAPSHOT_LOADED;
        return RecoveryStatus.ADVANCED;
    }

    public RecoveryStatus journalReplayed(
            final CoreRecoveryState state, final boolean replayComplete) {
        if (stage != RecoveryStage.SNAPSHOT_LOADED) return RecoveryStatus.INVALID_PREDECESSOR;
        if (!replayComplete || !invariantChecker.valid(state)) return fail();
        stage = RecoveryStage.JOURNAL_REPLAYED;
        return RecoveryStatus.ADVANCED;
    }

    public RecoveryStatus advance() {
        return switch (stage) {
            case JOURNAL_REPLAYED ->
                    allVenues(VenueRecoveryPort::privateConnected)
                            ? move(RecoveryStage.PRIVATE_CONNECTED)
                            : RecoveryStatus.WAITING_FOR_EVIDENCE;
            case PRIVATE_CONNECTED ->
                    allVenues(VenueRecoveryPort::openOrdersAuthoritative)
                            ? move(RecoveryStage.ORDERS_RECONCILED)
                            : RecoveryStatus.WAITING_FOR_EVIDENCE;
            case ORDERS_RECONCILED ->
                    allVenues(VenueRecoveryPort::positionsAndBalancesAuthoritative)
                            ? move(RecoveryStage.POSITIONS_RECONCILED)
                            : RecoveryStatus.WAITING_FOR_EVIDENCE;
            case POSITIONS_RECONCILED ->
                    allBooks(BookRecoveryPort::trustedAndFresh)
                            ? move(RecoveryStage.BOOKS_TRUSTED)
                            : RecoveryStatus.WAITING_FOR_EVIDENCE;
            case BOOKS_TRUSTED ->
                    allBooks(BookRecoveryPort::warmupComplete)
                            ? move(RecoveryStage.WARMED)
                            : RecoveryStatus.WAITING_FOR_EVIDENCE;
            case WARMED -> move(RecoveryStage.DISARMED_READY);
            case FAILED -> RecoveryStatus.FAILED;
            default -> RecoveryStatus.INVALID_PREDECESSOR;
        };
    }

    public void failClosed() {
        stage = RecoveryStage.FAILED;
    }

    public RecoveryStage stage() {
        return stage;
    }

    private RecoveryStatus move(final RecoveryStage next) {
        stage = next;
        return RecoveryStatus.ADVANCED;
    }

    private RecoveryStatus fail() {
        stage = RecoveryStage.FAILED;
        return RecoveryStatus.FAILED;
    }

    private boolean allVenues(final VenueEvidence evidence) {
        for (VenueRecoveryPort venue : venues)
            if (venue == null || !evidence.test(venue)) return false;
        return true;
    }

    private boolean allBooks(final BookEvidence evidence) {
        for (BookRecoveryPort book : books) if (book == null || !evidence.test(book)) return false;
        return true;
    }

    @FunctionalInterface
    private interface VenueEvidence {
        boolean test(VenueRecoveryPort port);
    }

    @FunctionalInterface
    private interface BookEvidence {
        boolean test(BookRecoveryPort port);
    }
}
