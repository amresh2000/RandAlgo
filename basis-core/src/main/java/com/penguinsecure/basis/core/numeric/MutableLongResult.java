package com.penguinsecure.basis.core.numeric;

/** Caller-owned result slot used by checked hot-path numeric operations. */
public final class MutableLongResult {
    private long value;
    private NumericStatus status = NumericStatus.EMPTY;

    public long value() {
        return value;
    }

    public NumericStatus status() {
        return status;
    }

    public boolean isOk() {
        return status == NumericStatus.OK;
    }

    MutableLongResult set(final long newValue) {
        value = newValue;
        status = NumericStatus.OK;
        return this;
    }

    public MutableLongResult fail(final NumericStatus failure) {
        if (failure == null || failure == NumericStatus.OK) {
            throw new IllegalArgumentException("a non-OK failure status is required");
        }
        value = 0;
        status = failure;
        return this;
    }
}
