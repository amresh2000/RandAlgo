package com.penguinsecure.basis.app.operator;

import com.penguinsecure.basis.core.time.MonotonicClock;
import com.penguinsecure.basis.journal.codec.JournalEventEncoder;
import com.penguinsecure.basis.journal.ingress.JournalEventClass;
import com.penguinsecure.basis.journal.ingress.JournalOfferStatus;
import com.penguinsecure.basis.protocol.sbe.ControlAction;
import com.penguinsecure.basis.protocol.sbe.EventType;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderEncoder;
import com.penguinsecure.basis.protocol.sbe.OperatorControlEncoder;
import com.penguinsecure.basis.protocol.sbe.Venue;
import org.agrona.concurrent.UnsafeBuffer;

/** Encodes each admitted mutation as a critical durable operator-control fact. */
public final class JournalOperatorAuditSink implements OperatorAuditSink {
    private final JournalEventEncoder journal;
    private final MonotonicClock clock;
    private final int producerId;
    private final int cellId;
    private final UnsafeBuffer frame =
            new UnsafeBuffer(
                    new byte
                            [MessageHeaderEncoder.ENCODED_LENGTH
                                    + OperatorControlEncoder.BLOCK_LENGTH]);
    private final OperatorControlEncoder encoder = new OperatorControlEncoder();
    private final MessageHeaderEncoder header = new MessageHeaderEncoder();
    private long producerSequence;

    public JournalOperatorAuditSink(
            final JournalEventEncoder journal,
            final MonotonicClock clock,
            final int producerId,
            final int cellId,
            final long initialProducerSequence) {
        if (journal == null || clock == null) {
            throw new NullPointerException("dependencies are required");
        }
        if (producerId <= 0
                || producerId > 65_534
                || cellId <= 0
                || cellId > 65_534
                || initialProducerSequence < 0) {
            throw new IllegalArgumentException("invalid audit identity or sequence");
        }
        this.journal = journal;
        this.clock = clock;
        this.producerId = producerId;
        this.cellId = cellId;
        producerSequence = initialProducerSequence;
    }

    @Override
    public boolean audit(final MutableOperatorCommand command) {
        if (command == null || !command.action().mutating()) {
            return false;
        }
        final long now = clock.nanoTime();
        encoder.wrapAndApplyHeader(frame, 0, header);
        encoder.eventHeader()
                .eventType(EventType.OPERATOR_CONTROL)
                .eventSequence(0)
                .producerId(producerId)
                .producerEpoch(1)
                .cellId(cellId)
                .venue(Venue.NULL_VAL)
                .accountId(0)
                .instrumentId(0)
                .strategyId(0)
                .configurationGeneration(command.expectedConfigurationGeneration())
                .sessionGeneration(command.controlGeneration())
                .correlationId(command.operatorId())
                .exchangeEpochNanos(0)
                .localReceiveEpochNanos(command.issuedEpochNanos())
                .localReceiveMonoNanos(now)
                .causeEventSequence(0)
                .flags((command.role().ordinal() << 16) | command.scopeType())
                .reasonCode(command.reasonCode());
        encoder.action(controlAction(command.action()))
                .scopeId(command.scopeId())
                .effectiveMonoNanos(now)
                .approvalHashHigh(command.idHigh())
                .approvalHashLow(command.idLow());
        final JournalOfferStatus status =
                journal.offer(
                        JournalEventClass.CRITICAL,
                        ++producerSequence,
                        frame,
                        0,
                        frame.capacity(),
                        now);
        return status == JournalOfferStatus.ACCEPTED;
    }

    private static ControlAction controlAction(final OperatorAction action) {
        return switch (action) {
            case ARM -> ControlAction.ARM;
            case DISARM -> ControlAction.DISARM;
            case KILL -> ControlAction.KILL;
            case CANCEL_ALL -> ControlAction.CANCEL_ALL;
            case RECONCILE -> ControlAction.RECONCILE;
            case SNAPSHOT -> ControlAction.SNAPSHOT;
            case STAGE_CONFIG -> ControlAction.STAGE_CONFIG;
            case ACTIVATE_CONFIG -> ControlAction.ACTIVATE_CONFIG;
            case SHUTDOWN -> ControlAction.SHUTDOWN;
            case STATUS -> throw new IllegalArgumentException("status is not a mutation");
        };
    }
}
