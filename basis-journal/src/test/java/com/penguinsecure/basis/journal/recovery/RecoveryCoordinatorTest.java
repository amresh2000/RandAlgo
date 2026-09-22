package com.penguinsecure.basis.journal.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.recovery.CoreRecoveryState;
import org.junit.jupiter.api.Test;

final class RecoveryCoordinatorTest {
    @Test
    void requiresEveryEvidenceStepAndStopsDisarmed() {
        MutableVenue firstVenue = new MutableVenue();
        MutableVenue secondVenue = new MutableVenue();
        MutableBook firstBook = new MutableBook();
        MutableBook secondBook = new MutableBook();
        RecoveryCoordinator coordinator =
                new RecoveryCoordinator(
                        new VenueRecoveryPort[] {firstVenue, secondVenue},
                        new BookRecoveryPort[] {firstBook, secondBook},
                        new RecoveryInvariantChecker());
        CoreRecoveryState state = new CoreRecoveryState(1, 1, 1, 1, 1, 1);

        assertEquals(RecoveryStatus.ADVANCED, coordinator.snapshotLoaded(state, true));
        assertEquals(RecoveryStatus.ADVANCED, coordinator.journalReplayed(state, true));
        assertEquals(RecoveryStatus.WAITING_FOR_EVIDENCE, coordinator.advance());
        firstVenue.connected = secondVenue.connected = true;
        assertEquals(RecoveryStatus.ADVANCED, coordinator.advance());
        firstVenue.orders = secondVenue.orders = true;
        assertEquals(RecoveryStatus.ADVANCED, coordinator.advance());
        firstVenue.positions = secondVenue.positions = true;
        assertEquals(RecoveryStatus.ADVANCED, coordinator.advance());
        firstBook.trusted = secondBook.trusted = true;
        assertEquals(RecoveryStatus.ADVANCED, coordinator.advance());
        firstBook.warmed = secondBook.warmed = true;
        assertEquals(RecoveryStatus.ADVANCED, coordinator.advance());
        assertEquals(RecoveryStatus.ADVANCED, coordinator.advance());
        assertEquals(RecoveryStage.DISARMED_READY, coordinator.stage());
        assertEquals(RecoveryStatus.INVALID_PREDECESSOR, coordinator.advance());
    }

    private static final class MutableVenue implements VenueRecoveryPort {
        private boolean connected;
        private boolean orders;
        private boolean positions;

        @Override
        public boolean privateConnected() {
            return connected;
        }

        @Override
        public boolean openOrdersAuthoritative() {
            return orders;
        }

        @Override
        public boolean positionsAndBalancesAuthoritative() {
            return positions;
        }
    }

    private static final class MutableBook implements BookRecoveryPort {
        private boolean trusted;
        private boolean warmed;

        @Override
        public boolean trustedAndFresh() {
            return trusted;
        }

        @Override
        public boolean warmupComplete() {
            return warmed;
        }
    }
}
