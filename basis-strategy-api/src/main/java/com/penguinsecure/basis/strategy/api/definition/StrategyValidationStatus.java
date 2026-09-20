package com.penguinsecure.basis.strategy.api.definition;

/** Stable onboarding validation outcome. */
public enum StrategyValidationStatus {
    VALID(0),
    INVALID_IDENTITY(1),
    INVALID_VERSION(2),
    INVALID_LIFECYCLE(3),
    INVALID_EFFECTIVE_TIME(4),
    INVALID_LEG(5),
    DUPLICATE_LEG(6),
    INVALID_CURRENCY(7),
    INVALID_MODEL_ID(8),
    INVALID_ECONOMIC_SOURCE(9),
    INVALID_THRESHOLD(10),
    INVALID_TIME_LIMIT(11),
    INVALID_PERFORMANCE_BUDGET(12),
    INVALID_RISK_LIMIT(13);

    private final int code;

    StrategyValidationStatus(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
