package com.penguinsecure.basis.sim.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
final class SanitizedRotatingRawFrameWriterTest {
    @Test
    void rotatesWithinBoundAndRejectsCredentialMaterial(@TempDir Path directory)
            throws IOException {
        byte[] publicFrame =
                "{\"topic\":\"orderbook.50.BTCUSDT\"}".getBytes(StandardCharsets.US_ASCII);
        byte[] secretFrame =
                "{\"access_token\":\"never-write-me\"}".getBytes(StandardCharsets.US_ASCII);
        try (SanitizedRotatingRawFrameWriter writer =
                new SanitizedRotatingRawFrameWriter(directory, 80, 2)) {
            writer.onFrame(1, 2, 3, publicFrame, 0, publicFrame.length);
            writer.onFrame(1, 4, 5, publicFrame, 0, publicFrame.length);
            writer.onFrame(2, 6, 7, publicFrame, 0, publicFrame.length);
            writer.onFrame(2, 8, 9, secretFrame, 0, secretFrame.length);
            assertEquals(3, writer.writtenFrames());
            assertEquals(1, writer.rejectedFrames());
        }

        try (var files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                assertFalse(content.contains("never-write-me"));
            }
        }
    }
}
