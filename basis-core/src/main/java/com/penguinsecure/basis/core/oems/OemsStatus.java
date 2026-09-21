package com.penguinsecure.basis.core.oems;

/** Expected result of an OEMS state transition. */
public enum OemsStatus {
    OK,
    DUPLICATE,
    STALE_HANDLE,
    CONFLICT,
    CAPACITY_EXHAUSTED,
    INVALID_STATE,
    NUMERIC_FAILURE
}
