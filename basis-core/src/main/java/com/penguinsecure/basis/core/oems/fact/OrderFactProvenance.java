package com.penguinsecure.basis.core.oems.fact;

/** Stable source classification for execution facts. */
public enum OrderFactProvenance {
    SIMULATED(1),
    COUNTERFACTUAL(2),
    ACTUAL(3);

    private final int code;

    OrderFactProvenance(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static OrderFactProvenance fromCode(final int code) {
        return switch (code) {
            case 1 -> SIMULATED;
            case 2 -> COUNTERFACTUAL;
            case 3 -> ACTUAL;
            default -> throw new IllegalArgumentException("unknown provenance code");
        };
    }
}
