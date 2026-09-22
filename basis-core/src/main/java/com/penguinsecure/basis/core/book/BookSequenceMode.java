package com.penguinsecure.basis.core.book;

/** Certified mutation family; neither mode invents exact consecutive sequencing. */
public enum BookSequenceMode {
    SNAPSHOT_DELTA_MONOTONIC,
    COMPLETE_IMAGE_MONOTONIC
}
