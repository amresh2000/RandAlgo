package com.penguinsecure.basis.app.operator;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Fixed-capacity SPSC lane from authenticated cold ingress to the core owner. */
public final class OperatorCommandLane {
    private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);
    private final int capacity, maximumPayload;
    private final long[] high,
            low,
            operator,
            expectedConfig,
            generation,
            issued,
            expires,
            published;
    private final int[] roles, actions, scopeTypes, scopeIds, reasons, payloadLengths;
    private final byte[] payloads;
    private final MutableOperatorCommand view;
    private volatile long producerSequence, consumerSequence, failedClaims;

    public OperatorCommandLane(final int capacity, final int maximumPayload) {
        if (capacity <= 0 || maximumPayload < 0)
            throw new IllegalArgumentException("invalid lane bounds");
        this.capacity = capacity;
        this.maximumPayload = maximumPayload;
        high = new long[capacity];
        low = new long[capacity];
        operator = new long[capacity];
        expectedConfig = new long[capacity];
        generation = new long[capacity];
        issued = new long[capacity];
        expires = new long[capacity];
        published = new long[capacity];
        roles = new int[capacity];
        actions = new int[capacity];
        scopeTypes = new int[capacity];
        scopeIds = new int[capacity];
        reasons = new int[capacity];
        payloadLengths = new int[capacity];
        payloads = new byte[Math.multiplyExact(capacity, maximumPayload)];
        view = new MutableOperatorCommand(maximumPayload);
    }

    public boolean tryPublish(final SignedOperatorRequest request) {
        final byte[] payload = request.payload();
        if (payload.length > maximumPayload || producerSequence - consumerSequence >= capacity) {
            failedClaims++;
            return false;
        }
        final int index = (int) (producerSequence % capacity);
        high[index] = request.commandIdHigh();
        low[index] = request.commandIdLow();
        operator[index] = request.operatorId();
        roles[index] = request.role().ordinal();
        actions[index] = request.action().ordinal();
        expectedConfig[index] = request.expectedConfigurationGeneration();
        generation[index] = request.controlGeneration();
        scopeTypes[index] = request.scopeType();
        scopeIds[index] = request.scopeId();
        reasons[index] = request.reasonCode();
        issued[index] = request.issuedEpochNanos();
        expires[index] = request.expiresEpochNanos();
        payloadLengths[index] = payload.length;
        System.arraycopy(payload, 0, payloads, index * maximumPayload, payload.length);
        LONGS.setRelease(published, index, producerSequence + 1);
        producerSequence++;
        return true;
    }

    public int drain(final OperatorCommandHandler handler, final int limit) {
        if (handler == null || limit <= 0) return 0;
        int count = 0;
        while (count < limit && consumerSequence < producerSequence) {
            final int index = (int) (consumerSequence % capacity);
            if ((long) LONGS.getAcquire(published, index) != consumerSequence + 1) break;
            view.set(
                    high[index],
                    low[index],
                    operator[index],
                    OperatorRole.values()[roles[index]],
                    OperatorAction.values()[actions[index]],
                    expectedConfig[index],
                    generation[index],
                    scopeTypes[index],
                    scopeIds[index],
                    reasons[index],
                    issued[index],
                    expires[index],
                    payloads,
                    index * maximumPayload,
                    payloadLengths[index]);
            handler.onCommand(view);
            LONGS.setRelease(published, index, 0L);
            consumerSequence++;
            count++;
        }
        return count;
    }

    public int size() {
        return (int) (producerSequence - consumerSequence);
    }

    public long failedClaims() {
        return failedClaims;
    }

    public long oldestAgeNanos(final long nowEpochNanos) {
        if (consumerSequence >= producerSequence) return 0;
        final long value = issued[(int) (consumerSequence % capacity)];
        return nowEpochNanos >= value ? nowEpochNanos - value : Long.MAX_VALUE;
    }
}
