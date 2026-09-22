package com.penguinsecure.basis.core.risk;

/** Core-owned hedge-path permission state. */
public enum HedgePathHealthState {
    HEALTHY,
    DEGRADED,
    UNSAFE,
    RECOVERING
}
