package com.penguinsecure.basis.app.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.app.lifecycle.ExecutionCellLifecycle;
import com.penguinsecure.basis.core.risk.KillHierarchy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class OperatorControlTest {
    private static final byte[] KEY =
            "operator-control-test-key".getBytes(StandardCharsets.US_ASCII);
    private static final long NOW = 2_000;

    @Test
    void gatewayAuthenticatesAuthorizesExpiresAndBoundsIngress() {
        try (OperatorAuthenticator authenticator =
                new OperatorAuthenticator(17, OperatorRole.ADMIN, KEY)) {
            final OperatorCommandLane lane = new OperatorCommandLane(1, 32);
            final OperatorGateway gateway = new OperatorGateway(authenticator, lane);

            assertEquals(
                    OperatorCommandStatus.UNAUTHORIZED,
                    gateway.submit(
                            signed(authenticator, 1, OperatorRole.VIEWER, OperatorAction.ARM),
                            NOW));
            assertEquals(
                    OperatorCommandStatus.EXPIRED,
                    gateway.submit(
                            signed(authenticator, 2, OperatorRole.ADMIN, OperatorAction.STATUS),
                            4_000));
            assertEquals(
                    OperatorCommandStatus.ACCEPTED,
                    gateway.submit(
                            signed(authenticator, 3, OperatorRole.ADMIN, OperatorAction.STATUS),
                            NOW));
            assertEquals(
                    OperatorCommandStatus.CAPACITY_EXHAUSTED,
                    gateway.submit(
                            signed(authenticator, 4, OperatorRole.ADMIN, OperatorAction.STATUS),
                            NOW));
        }
    }

    @Test
    void auditsBeforeMutationAndReplaysCachedResultForDuplicate() {
        try (OperatorAuthenticator authenticator =
                new OperatorAuthenticator(17, OperatorRole.ADMIN, KEY)) {
            final OperatorCommandLane commands = new OperatorCommandLane(4, 32);
            final OperatorResultLane results = new OperatorResultLane(4);
            final FakeActions actions = new FakeActions();
            final int[] audits = {0};
            final OperatorCommandProcessor processor =
                    new OperatorCommandProcessor(
                            new ExecutionCellLifecycle(),
                            new KillHierarchy(8),
                            command -> {
                                audits[0]++;
                                return true;
                            },
                            actions,
                            results,
                            new OperatorResultCache(4),
                            () -> NOW);
            final SignedOperatorRequest request =
                    signed(authenticator, 10, OperatorRole.TRADER, OperatorAction.CANCEL_ALL);

            assertTrue(commands.tryPublish(request));
            assertTrue(commands.tryPublish(request));
            assertEquals(2, commands.drain(processor, 4));
            assertEquals(1, audits[0]);
            assertEquals(1, actions.cancelCount);

            final List<OperatorCommandStatus> statuses = new ArrayList<>();
            assertEquals(2, results.drain(result -> statuses.add(result.status()), 4));
            assertEquals(
                    List.of(OperatorCommandStatus.APPLIED, OperatorCommandStatus.APPLIED),
                    statuses);
        }
    }

    @Test
    void failedAuditPreventsMutationAndSlowResultConsumerCannotBlockCore() {
        final OperatorCommandLane commands = new OperatorCommandLane(4, 32);
        final OperatorResultLane results = new OperatorResultLane(1);
        final FakeActions actions = new FakeActions();
        final OperatorCommandProcessor processor =
                new OperatorCommandProcessor(
                        new ExecutionCellLifecycle(),
                        new KillHierarchy(8),
                        command -> command.idLow() != 20,
                        actions,
                        results,
                        new OperatorResultCache(4),
                        () -> NOW);
        try (OperatorAuthenticator authenticator =
                new OperatorAuthenticator(17, OperatorRole.ADMIN, KEY)) {
            assertTrue(
                    commands.tryPublish(
                            signed(
                                    authenticator,
                                    20,
                                    OperatorRole.TRADER,
                                    OperatorAction.CANCEL_ALL)));
            assertTrue(
                    commands.tryPublish(
                            signed(
                                    authenticator,
                                    21,
                                    OperatorRole.TRADER,
                                    OperatorAction.CANCEL_ALL)));
        }

        assertEquals(2, commands.drain(processor, 4));
        assertEquals(1, actions.cancelCount);
        assertEquals(1, results.size());
        assertEquals(1, results.dropped());
        final List<OperatorCommandStatus> statuses = new ArrayList<>();
        results.drain(result -> statuses.add(result.status()), 1);
        assertEquals(List.of(OperatorCommandStatus.AUDIT_FAILED), statuses);
    }

    private static SignedOperatorRequest signed(
            final OperatorAuthenticator authenticator,
            final long id,
            final OperatorRole role,
            final OperatorAction action) {
        final SignedOperatorRequest unsigned =
                new SignedOperatorRequest(
                        11,
                        id,
                        17,
                        role,
                        action,
                        1,
                        id,
                        0,
                        0,
                        1,
                        1_000,
                        3_000,
                        new byte[0],
                        new byte[32]);
        return new SignedOperatorRequest(
                unsigned.commandIdHigh(),
                unsigned.commandIdLow(),
                unsigned.operatorId(),
                unsigned.role(),
                unsigned.action(),
                unsigned.expectedConfigurationGeneration(),
                unsigned.controlGeneration(),
                unsigned.scopeType(),
                unsigned.scopeId(),
                unsigned.reasonCode(),
                unsigned.issuedEpochNanos(),
                unsigned.expiresEpochNanos(),
                unsigned.payload(),
                authenticator.sign(unsigned));
    }

    private static final class FakeActions implements OperatorActionPort {
        private int cancelCount;

        @Override
        public boolean cancelAll(final int scopeType, final int scopeId) {
            cancelCount++;
            return true;
        }

        @Override
        public boolean reconcile(final int scopeType, final int scopeId) {
            return true;
        }

        @Override
        public boolean snapshot() {
            return true;
        }

        @Override
        public boolean stageConfiguration(
                final byte[] payload, final int length, final long generation) {
            return true;
        }

        @Override
        public boolean activateConfiguration(final long generation) {
            return true;
        }

        @Override
        public boolean beginShutdown(final long generation) {
            return true;
        }
    }
}
