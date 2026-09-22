package com.penguinsecure.basis.journal.recovery;

/** Future public venue adapters report new-session book trust and warm-up evidence here. */
public interface BookRecoveryPort {
    boolean trustedAndFresh();

    boolean warmupComplete();
}
