package com.penguinsecure.basis.sim.capture;

import static org.junit.jupiter.api.Assertions.*;

import com.penguinsecure.basis.venue.api.json.ReadableBytes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class BoundedRawFrameCaptureTest {
    @Test
    void copiesFramesAndCountsDropsWithoutBlocking() {
        BoundedRawFrameCapture capture = new BoundedRawFrameCapture(2, 16);
        assertTrue(offer(capture, "one"));
        assertTrue(offer(capture, "two"));
        assertFalse(offer(capture, "three"));
        assertEquals(1, capture.droppedFrames());
        List<String> frames = new ArrayList<>();
        assertEquals(
                2,
                capture.drain(
                        (venue, epoch, mono, bytes, offset, length) ->
                                frames.add(
                                        new String(
                                                bytes, offset, length, StandardCharsets.US_ASCII)),
                        2));
        assertEquals(List.of("one", "two"), frames);
        assertTrue(offer(capture, "four"));
    }

    private static boolean offer(BoundedRawFrameCapture capture, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        ReadableBytes input = index -> bytes[index];
        return capture.offer(1, 2, 3, input, 0, bytes.length);
    }
}
