package com.penguinsecure.basis.venue.deribit.order;

/** Warm-thread authenticated HTTP seam. */
@FunctionalInterface
public interface DeribitReconciliationTransport {
    boolean fetch(
            DeribitReconciliationEndpoint endpoint,
            int offset,
            long startTimeMillis,
            DeribitReconciliationPage destination);
}
