package com.penguinsecure.basis.app.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.app.config.ConfigurationSignatureVerifier;
import com.penguinsecure.basis.app.config.SignedConfiguration;
import com.penguinsecure.basis.app.config.SignedConfigurationCodec;
import com.penguinsecure.basis.app.config.StagedConfigurationStore;
import com.penguinsecure.basis.app.lifecycle.ExecutionCellLifecycle;
import com.penguinsecure.basis.app.lifecycle.StartupEvidence;
import com.penguinsecure.basis.journal.recovery.RecoveryStage;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class ExecutionCellActionPortTest {
    @Test
    void activationAdvancesLifecycleGenerationOnlyWhileDisarmed() {
        final byte[] key = "configuration-test-key".getBytes(StandardCharsets.US_ASCII);
        try (ConfigurationSignatureVerifier verifier = new ConfigurationSignatureVerifier(key)) {
            final ExecutionCellLifecycle lifecycle = readyLifecycle();
            final StagedConfigurationStore store = new StagedConfigurationStore(verifier);
            final ShutdownCoordinator shutdown =
                    new ShutdownCoordinator(lifecycle, new NoopShutdown(), () -> 0, 100);
            final ExecutionCellActionPort actions =
                    new ExecutionCellActionPort(
                            new NoopEffects(), store, lifecycle, shutdown, () -> 2_000);

            final byte[] second = encoded(verifier, 2);
            assertTrue(actions.stageConfiguration(second, second.length, 2));
            assertTrue(actions.activateConfiguration(2));
            assertEquals(2, lifecycle.configurationGeneration());

            assertEquals(
                    com.penguinsecure.basis.app.lifecycle.LifecycleStatus.APPLIED,
                    lifecycle.arm(2, 1));
            final byte[] third = encoded(verifier, 3);
            assertTrue(actions.stageConfiguration(third, third.length, 3));
            assertFalse(actions.activateConfiguration(3));
            assertEquals(2, lifecycle.configurationGeneration());
            assertEquals(3, store.stagedGeneration());
        }
    }

    private static byte[] encoded(
            final ConfigurationSignatureVerifier verifier, final long generation) {
        final byte[] payload = {(byte) generation};
        return SignedConfigurationCodec.encode(
                new SignedConfiguration(
                        1,
                        generation,
                        1_000,
                        3_000,
                        payload,
                        verifier.sign(1, generation, 1_000, 3_000, payload)));
    }

    private static ExecutionCellLifecycle readyLifecycle() {
        final ExecutionCellLifecycle lifecycle = new ExecutionCellLifecycle();
        lifecycle.beginRecovery();
        lifecycle.observeStartup(
                new StartupEvidence(RecoveryStage.DISARMED_READY, 1, true, true, true));
        return lifecycle;
    }

    private static final class NoopEffects implements OperationalEffects {
        @Override
        public boolean cancelAll(final int scopeType, final int scopeId) {
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
    }

    private static final class NoopShutdown implements ShutdownPort {
        @Override
        public ShutdownStepStatus drainAndResolve(final long deadlineNanos) {
            return ShutdownStepStatus.COMPLETE;
        }

        @Override
        public boolean reconcile() {
            return true;
        }

        @Override
        public boolean snapshot() {
            return true;
        }

        @Override
        public boolean flushJournal() {
            return true;
        }

        @Override
        public boolean closeOrderAndPrivateChannels() {
            return true;
        }

        @Override
        public boolean closeMarketDataAndInfrastructure() {
            return true;
        }
    }
}
