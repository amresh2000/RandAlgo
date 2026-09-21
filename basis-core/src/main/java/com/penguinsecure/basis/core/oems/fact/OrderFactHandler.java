package com.penguinsecure.basis.core.oems.fact;

/** Single-writer callback; the supplied mutable fact must not escape the call. */
@FunctionalInterface
public interface OrderFactHandler {
    void onOrderFact(MutableOrderFact fact);
}
