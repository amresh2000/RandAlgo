package com.penguinsecure.basis.app.operator;

/** Cold ingress boundary: authenticate and authorize before touching the bounded lane. */
public final class OperatorGateway {
    private final OperatorAuthenticator authenticator;
    private final OperatorCommandLane lane;

    public OperatorGateway(
            final OperatorAuthenticator authenticator, final OperatorCommandLane lane) {
        if (authenticator == null || lane == null)
            throw new NullPointerException("dependencies are required");
        this.authenticator = authenticator;
        this.lane = lane;
    }

    public OperatorCommandStatus submit(
            final SignedOperatorRequest request, final long nowEpochNanos) {
        if (request == null) return OperatorCommandStatus.INVALID;
        if (nowEpochNanos > request.expiresEpochNanos()) return OperatorCommandStatus.EXPIRED;
        if (!authenticator.verify(request, nowEpochNanos))
            return OperatorCommandStatus.UNAUTHENTICATED;
        if (!request.action().authorized(request.role())) return OperatorCommandStatus.UNAUTHORIZED;
        return lane.tryPublish(request)
                ? OperatorCommandStatus.ACCEPTED
                : OperatorCommandStatus.CAPACITY_EXHAUSTED;
    }
}
