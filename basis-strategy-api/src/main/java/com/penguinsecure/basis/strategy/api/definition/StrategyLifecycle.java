package com.penguinsecure.basis.strategy.api.definition;

/** Certification lifecycle of one immutable strategy version. */
public enum StrategyLifecycle {
    DRAFT,
    CONTRACT_VALIDATED,
    REPLAY_CERTIFIED,
    SHADOW,
    TESTNET_CERTIFIED,
    CANARY,
    ACTIVE,
    SUSPENDED,
    RETIRED
}
