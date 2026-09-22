package com.penguinsecure.basis.journal.config;

import java.nio.file.Path;

/** Immutable, validated journal configuration. Secrets deliberately do not belong here. */
public record JournalConfiguration(
        Path mediaDriverDirectory,
        Path archiveDirectory,
        Path snapshotDirectory,
        String channel,
        int streamId,
        int replayStreamId,
        int losslessCapacityBytes,
        int lossyCapacityBytes,
        int criticalReserveBytes,
        int maximumOutstandingChildren,
        int maximumTerminalBytesPerChild,
        long archiveLagHighWaterBytes,
        long diskLowWaterBytes) {
    public JournalConfiguration {
        requireDirectory(mediaDriverDirectory, "mediaDriverDirectory");
        requireDirectory(archiveDirectory, "archiveDirectory");
        requireDirectory(snapshotDirectory, "snapshotDirectory");
        if (mediaDriverDirectory.equals(archiveDirectory)) {
            throw new IllegalArgumentException("media driver and archive directories must differ");
        }
        if (channel == null || channel.isBlank() || streamId <= 0 || replayStreamId <= 0) {
            throw new IllegalArgumentException("explicit channel and positive stream IDs required");
        }
        requirePowerOfTwo(losslessCapacityBytes, "losslessCapacityBytes");
        requirePowerOfTwo(lossyCapacityBytes, "lossyCapacityBytes");
        if (criticalReserveBytes <= 0 || criticalReserveBytes >= losslessCapacityBytes) {
            throw new IllegalArgumentException("critical reserve must be within lossless capacity");
        }
        final long minimumReserve =
                minimumCriticalReserve(maximumOutstandingChildren, maximumTerminalBytesPerChild);
        if (criticalReserveBytes < minimumReserve) {
            throw new IllegalArgumentException(
                    "critical reserve "
                            + criticalReserveBytes
                            + " is below minimum "
                            + minimumReserve);
        }
        if (archiveLagHighWaterBytes <= 0 || diskLowWaterBytes <= 0) {
            throw new IllegalArgumentException("positive lag and disk watermarks required");
        }
    }

    public int normalLimitBytes() {
        return losslessCapacityBytes - criticalReserveBytes;
    }

    public static long minimumCriticalReserve(
            final int maximumOutstandingChildren, final int maximumTerminalBytesPerChild) {
        if (maximumOutstandingChildren < 0 || maximumTerminalBytesPerChild <= 0) {
            throw new IllegalArgumentException("invalid critical reserve inputs");
        }
        return Math.multiplyExact(
                (long) maximumOutstandingChildren, (long) maximumTerminalBytesPerChild);
    }

    private static void requireDirectory(final Path path, final String name) {
        if (path == null || !path.isAbsolute() || path.getParent() == null) {
            throw new IllegalArgumentException(
                    name + " must be an explicit non-root absolute path");
        }
    }

    private static void requirePowerOfTwo(final int value, final String name) {
        if (value < 1024 || Integer.bitCount(value) != 1) {
            throw new IllegalArgumentException(name + " must be a power of two >= 1024");
        }
    }
}
