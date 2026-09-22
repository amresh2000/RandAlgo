package com.penguinsecure.basis.journal.ingress;

/** Fail-closed journal health state. */
public enum JournalHealthState {
    HEALTHY,
    INITIATION_DISARMED,
    GLOBAL_FAULT
}
