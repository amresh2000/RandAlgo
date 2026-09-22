package com.penguinsecure.basis.venue.bybit.order;

/** Warm-thread HTTP seam; implementations must sign, bound, and redact requests. */
@FunctionalInterface
public interface BybitReconciliationTransport {
    boolean fetch(
            BybitReconciliationEndpoint endpoint,
            byte[] cursor,
            int cursorLength,
            long startTimeMillis,
            BybitReconciliationPage destination);
}
