package com.penguinsecure.basis.core.book;

/** Fail-closed lifecycle of one venue/instrument book. */
public enum BookTrustState {
    DISCONNECTED,
    SYNCING,
    WARMING,
    TRUSTED
}
