package com.penguinsecure.basis.journal.snapshot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Checksummed same-directory temporary write, force, verify, and atomic publish. */
public final class FileCoreSnapshotStore implements CoreSnapshotStore {
    private static final long MAGIC = 0x42534953534e4150L;
    private static final int CHECKSUM_LENGTH = 32;
    private static final int CHECKSUM_OFFSET = 80;
    private static final int HEADER_LENGTH = 112;
    private static final String PREFIX = "basis-snapshot-";
    private static final String SUFFIX = ".bin";
    private final Path directory;
    private final int maximumPayloadBytes;

    public FileCoreSnapshotStore(final Path directory, final int maximumPayloadBytes) {
        if (directory == null
                || !directory.isAbsolute()
                || directory.getParent() == null
                || maximumPayloadBytes <= 0) {
            throw new IllegalArgumentException("explicit directory and payload bound required");
        }
        this.directory = directory;
        this.maximumPayloadBytes = maximumPayloadBytes;
    }

    @Override
    public void write(final SnapshotDescriptor descriptor, final byte[] payload)
            throws IOException {
        if (descriptor == null || payload == null || payload.length > maximumPayloadBytes) {
            throw new IllegalArgumentException("invalid snapshot");
        }
        Files.createDirectories(directory);
        setOwnerOnly(directory, true);
        final Path destination = directory.resolve(filename(descriptor.snapshotId()));
        final Path temporary = directory.resolve(filename(descriptor.snapshotId()) + ".tmp");
        final byte[] encoded = encode(descriptor, payload);
        try (FileChannel channel =
                FileChannel.open(
                        temporary,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        setOwnerOnly(temporary, false);
        SnapshotLoadResult verified =
                read(
                        temporary,
                        descriptor.formatVersion(),
                        descriptor.schemaId(),
                        descriptor.schemaVersion(),
                        descriptor.buildGeneration(),
                        descriptor.configurationGeneration(),
                        descriptor.catalogGeneration());
        if (verified.status() != SnapshotLoadStatus.LOADED) {
            throw new IOException(
                    "snapshot read-back verification failed: " + verified.diagnostic());
        }
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic snapshot rename is not supported", exception);
        }
        forceDirectory();
    }

    @Override
    public SnapshotLoadResult loadLatestCompatible(
            final int formatVersion,
            final int schemaId,
            final int maximumSchemaVersion,
            final long buildGeneration,
            final long configurationGeneration,
            final long catalogGeneration) {
        if (!Files.isDirectory(directory)) {
            return SnapshotLoadResult.failure(
                    SnapshotLoadStatus.NOT_FOUND, "snapshot directory absent");
        }
        final List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(directory, PREFIX + "*" + SUFFIX)) {
            for (Path candidate : stream) candidates.add(candidate);
        } catch (IOException exception) {
            return SnapshotLoadResult.failure(
                    SnapshotLoadStatus.IO_FAILURE, exception.getClass().getSimpleName());
        }
        candidates.sort(Comparator.comparingLong(FileCoreSnapshotStore::idFromFilename).reversed());
        SnapshotLoadResult last =
                SnapshotLoadResult.failure(
                        SnapshotLoadStatus.NOT_FOUND, "no compatible verified snapshot");
        for (Path candidate : candidates) {
            last =
                    read(
                            candidate,
                            formatVersion,
                            schemaId,
                            maximumSchemaVersion,
                            buildGeneration,
                            configurationGeneration,
                            catalogGeneration);
            if (last.status() == SnapshotLoadStatus.LOADED) return last;
        }
        return last;
    }

    private SnapshotLoadResult read(
            final Path path,
            final int formatVersion,
            final int schemaId,
            final int maximumSchemaVersion,
            final long buildGeneration,
            final long configurationGeneration,
            final long catalogGeneration) {
        try {
            final long size = Files.size(path);
            if (size < HEADER_LENGTH || size > HEADER_LENGTH + (long) maximumPayloadBytes) {
                return SnapshotLoadResult.failure(
                        SnapshotLoadStatus.CORRUPT, "invalid file length");
            }
            final byte[] bytes = Files.readAllBytes(path);
            final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (buffer.getLong() != MAGIC) {
                return SnapshotLoadResult.failure(SnapshotLoadStatus.CORRUPT, "invalid magic");
            }
            final SnapshotDescriptor descriptor =
                    new SnapshotDescriptor(
                            buffer.getInt(),
                            buffer.getInt(),
                            buffer.getInt(),
                            buffer.getLong(),
                            buffer.getLong(),
                            buffer.getLong(),
                            buffer.getLong(),
                            buffer.getLong(),
                            buffer.getLong(),
                            buffer.getLong());
            final int payloadLength = buffer.getInt();
            final byte[] expectedChecksum = new byte[CHECKSUM_LENGTH];
            buffer.get(expectedChecksum);
            if (payloadLength < 0
                    || payloadLength > maximumPayloadBytes
                    || buffer.remaining() != payloadLength) {
                return SnapshotLoadResult.failure(
                        SnapshotLoadStatus.CORRUPT, "truncated or oversized payload");
            }
            final byte[] payload = new byte[payloadLength];
            buffer.get(payload);
            if (!MessageDigest.isEqual(expectedChecksum, checksum(bytes, payloadLength))) {
                return SnapshotLoadResult.failure(SnapshotLoadStatus.CORRUPT, "checksum mismatch");
            }
            if (descriptor.formatVersion() != formatVersion
                    || descriptor.schemaId() != schemaId
                    || descriptor.schemaVersion() > maximumSchemaVersion
                    || descriptor.buildGeneration() != buildGeneration
                    || descriptor.configurationGeneration() != configurationGeneration
                    || descriptor.catalogGeneration() != catalogGeneration) {
                return SnapshotLoadResult.failure(
                        SnapshotLoadStatus.INCOMPATIBLE, "snapshot identity mismatch");
            }
            return new SnapshotLoadResult(
                    SnapshotLoadStatus.LOADED, descriptor, payload, "verified");
        } catch (IOException | IllegalArgumentException exception) {
            return SnapshotLoadResult.failure(
                    SnapshotLoadStatus.CORRUPT, exception.getClass().getSimpleName());
        }
    }

    private static byte[] encode(final SnapshotDescriptor descriptor, final byte[] payload) {
        final ByteBuffer buffer =
                ByteBuffer.allocate(HEADER_LENGTH + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(MAGIC)
                .putInt(descriptor.formatVersion())
                .putInt(descriptor.schemaId())
                .putInt(descriptor.schemaVersion())
                .putLong(descriptor.buildGeneration())
                .putLong(descriptor.configurationGeneration())
                .putLong(descriptor.catalogGeneration())
                .putLong(descriptor.snapshotId())
                .putLong(descriptor.recordingId())
                .putLong(descriptor.recordingPosition())
                .putLong(descriptor.captureEpochNanos())
                .putInt(payload.length)
                .position(CHECKSUM_OFFSET + CHECKSUM_LENGTH);
        buffer.put(payload);
        final byte[] encoded = buffer.array();
        System.arraycopy(
                checksum(encoded, payload.length), 0, encoded, CHECKSUM_OFFSET, CHECKSUM_LENGTH);
        return encoded;
    }

    private static byte[] checksum(final byte[] encoded, final int payloadLength) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(encoded, 0, CHECKSUM_OFFSET);
            digest.update(encoded, HEADER_LENGTH, payloadLength);
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String filename(final long snapshotId) {
        return PREFIX + String.format(Locale.ROOT, "%020d", snapshotId) + SUFFIX;
    }

    private static long idFromFilename(final Path path) {
        final String name = path.getFileName().toString();
        try {
            return Long.parseLong(name.substring(PREFIX.length(), name.length() - SUFFIX.length()));
        } catch (RuntimeException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static void setOwnerOnly(final Path path, final boolean directory) {
        try {
            Files.setPosixFilePermissions(
                    path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Platform does not expose POSIX permissions; directory ownership is deployment-owned.
        }
    }

    private void forceDirectory() throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Directory fsync is not supported by every filesystem provider.
        }
    }
}
