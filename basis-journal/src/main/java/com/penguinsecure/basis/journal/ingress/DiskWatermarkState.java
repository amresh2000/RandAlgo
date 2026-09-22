package com.penguinsecure.basis.journal.ingress;

/** Archive filesystem watermark state. */
public enum DiskWatermarkState {
    HEALTHY,
    HIGH,
    CRITICAL
}
