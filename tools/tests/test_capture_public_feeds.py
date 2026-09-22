import io
import os
import struct
import unittest
from unittest.mock import patch

from tools.capture_public_feeds import MAX_FRAME_BYTES, decode_server_frame, encode_client_frame


class FakeSocket:
    def __init__(self, data: bytes):
        self.data = io.BytesIO(data)

    def recv(self, length: int) -> bytes:
        return self.data.read(length)


class FrameCodecTest(unittest.TestCase):
    @patch("os.urandom", return_value=b"\x01\x02\x03\x04")
    def test_client_text_frame_is_masked(self, _: object) -> None:
        frame = encode_client_frame(b"hello")
        self.assertEqual(frame[:2], b"\x81\x85")
        self.assertEqual(frame[2:6], b"\x01\x02\x03\x04")
        self.assertEqual(frame[6:], bytes(c ^ b"\x01\x02\x03\x04"[i % 4] for i, c in enumerate(b"hello")))

    def test_server_frame_supports_extended_length(self) -> None:
        payload = b"x" * 130
        wire = b"\x81\x7e" + struct.pack("!H", len(payload)) + payload
        self.assertEqual(decode_server_frame(FakeSocket(wire)), (True, 1, payload))

    def test_server_frame_rejects_oversize(self) -> None:
        wire = b"\x81\x7f" + struct.pack("!Q", MAX_FRAME_BYTES + 1)
        with self.assertRaisesRegex(ValueError, "exceeds"):
            decode_server_frame(FakeSocket(wire))


if __name__ == "__main__":
    unittest.main()
