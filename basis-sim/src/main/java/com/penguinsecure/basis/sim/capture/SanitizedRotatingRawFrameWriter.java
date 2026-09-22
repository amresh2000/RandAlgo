package com.penguinsecure.basis.sim.capture;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Cold-thread binary writer for public market-data frames with bounded file rotation. */
public final class SanitizedRotatingRawFrameWriter implements RawFrameHandler, Closeable {
    private static final int MAGIC = 0x424D4433;
    private static final int VERSION = 1;
    private static final int FILE_HEADER_BYTES = Integer.BYTES * 2;
    private static final int RECORD_HEADER_BYTES = Integer.BYTES * 2 + Long.BYTES * 2;
    private static final String[] FORBIDDEN_TOKENS = {
        "access_token", "refresh_token", "client_secret", "client_id", "api_key", "api_secret"
    };

    private final Path directory;
    private final long maximumFileBytes;
    private final int maximumFiles;
    private DataOutputStream output;
    private long currentFileBytes;
    private int nextFileIndex;
    private long writtenFrames;
    private long rejectedFrames;

    public SanitizedRotatingRawFrameWriter(
            final Path directory, final long maximumFileBytes, final int maximumFiles) {
        if (directory == null) throw new NullPointerException("directory is required");
        if (maximumFileBytes <= FILE_HEADER_BYTES + RECORD_HEADER_BYTES || maximumFiles <= 0) {
            throw new IllegalArgumentException("invalid rotation bounds");
        }
        this.directory = directory.toAbsolutePath().normalize();
        this.maximumFileBytes = maximumFileBytes;
        this.maximumFiles = maximumFiles;
        try {
            Files.createDirectories(this.directory);
            openNextFile();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public void onFrame(
            final int venueId,
            final long receiveEpochNanos,
            final long receiveMonoNanos,
            final byte[] bytes,
            final int offset,
            final int length) {
        if (bytes == null) throw new NullPointerException("bytes are required");
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IndexOutOfBoundsException("invalid frame range");
        }
        final long recordBytes = RECORD_HEADER_BYTES + (long) length;
        if (recordBytes + FILE_HEADER_BYTES > maximumFileBytes
                || containsForbiddenToken(bytes, offset, length)) {
            rejectedFrames++;
            return;
        }
        try {
            if (currentFileBytes + recordBytes > maximumFileBytes) openNextFile();
            output.writeInt(length);
            output.writeInt(venueId);
            output.writeLong(receiveEpochNanos);
            output.writeLong(receiveMonoNanos);
            output.write(bytes, offset, length);
            currentFileBytes += recordBytes;
            writtenFrames++;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public long writtenFrames() {
        return writtenFrames;
    }

    public long rejectedFrames() {
        return rejectedFrames;
    }

    @Override
    public void close() throws IOException {
        if (output != null) {
            output.flush();
            output.close();
            output = null;
        }
    }

    private void openNextFile() throws IOException {
        if (output != null) output.close();
        final Path file = directory.resolve(fileName(nextFileIndex));
        nextFileIndex = (nextFileIndex + 1) % maximumFiles;
        output =
                new DataOutputStream(
                        new BufferedOutputStream(
                                Files.newOutputStream(
                                        file,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.TRUNCATE_EXISTING,
                                        StandardOpenOption.WRITE)));
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        currentFileBytes = FILE_HEADER_BYTES;
    }

    private static boolean containsForbiddenToken(
            final byte[] bytes, final int offset, final int length) {
        final int end = offset + length;
        for (String token : FORBIDDEN_TOKENS) {
            final int limit = end - token.length();
            for (int start = offset; start <= limit; start++) {
                int index = 0;
                while (index < token.length()
                        && toLowerAscii(bytes[start + index]) == (byte) token.charAt(index))
                    index++;
                if (index == token.length()) return true;
            }
        }
        return false;
    }

    private static byte toLowerAscii(final byte value) {
        return value >= 'A' && value <= 'Z' ? (byte) (value + ('a' - 'A')) : value;
    }

    private static String fileName(final int index) {
        if (index < 10) return "market-data-00" + index + ".capture";
        if (index < 100) return "market-data-0" + index + ".capture";
        return "market-data-" + index + ".capture";
    }
}
