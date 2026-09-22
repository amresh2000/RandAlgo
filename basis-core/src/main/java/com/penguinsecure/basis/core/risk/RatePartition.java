package com.penguinsecure.basis.core.risk;

/** Independent rate-capacity pools. Normal initiation cannot borrow reserved capacity. */
public enum RatePartition {
    NORMAL,
    HEDGE,
    EMERGENCY
}
