package com.penguinsecure.basis.app.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.journal.codec.JournalEventEncoder;
import com.penguinsecure.basis.journal.ingress.JournalHealth;
import com.penguinsecure.basis.journal.ingress.JournalIngress;
import com.penguinsecure.basis.protocol.sbe.ControlAction;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderDecoder;
import com.penguinsecure.basis.protocol.sbe.OperatorControlDecoder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class JournalOperatorAuditSinkTest {
    @Test
    void writesReplayableCriticalOperatorFactBeforeMutation() {
        final JournalIngress ingress = new JournalIngress(4096, 512, 1024, 0, new JournalHealth());
        final JournalOperatorAuditSink audit =
                new JournalOperatorAuditSink(new JournalEventEncoder(ingress), () -> 123, 7, 9, 0);
        final MutableOperatorCommand command =
                new MutableOperatorCommand(0)
                        .set(
                                101,
                                202,
                                7,
                                OperatorRole.ADMIN,
                                OperatorAction.SHUTDOWN,
                                11,
                                12,
                                3,
                                4,
                                5,
                                1_000,
                                2_000,
                                new byte[0],
                                0,
                                0);

        assertTrue(audit.audit(command));
        final int[] observed = new int[1];
        assertEquals(
                1,
                ingress.drainLossless(
                        (sequence, templateId, buffer, offset, length) -> {
                            final MessageHeaderDecoder header =
                                    new MessageHeaderDecoder().wrap(buffer, offset);
                            final OperatorControlDecoder control =
                                    new OperatorControlDecoder()
                                            .wrap(
                                                    buffer,
                                                    offset + MessageHeaderDecoder.ENCODED_LENGTH,
                                                    header.blockLength(),
                                                    header.version());
                            assertEquals(ControlAction.SHUTDOWN, control.action());
                            assertEquals(4, control.scopeId());
                            assertEquals(101, control.approvalHashHigh());
                            assertEquals(202, control.approvalHashLow());
                            assertEquals(11, control.eventHeader().configurationGeneration());
                            assertEquals(12, control.eventHeader().sessionGeneration());
                            assertEquals(7, control.eventHeader().correlationId());
                            assertEquals(5, control.eventHeader().reasonCode());
                            observed[0]++;
                        },
                        1));
        assertEquals(1, observed[0]);
    }
}
