#!/usr/bin/env python3
"""Capture concurrent Bybit and Deribit public order-book frames as JSONL.

The script intentionally uses only the Python standard library so a clean
research host can run it without installing an exchange SDK. It records both
epoch and process-local monotonic receive timestamps before JSON decoding.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import socket
import ssl
import struct
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


MAX_FRAME_BYTES = 4 * 1024 * 1024


@dataclass(frozen=True)
class Feed:
    name: str
    url: str
    subscription: dict[str, object]


FEEDS = (
    Feed(
        name="bybit",
        url="wss://stream.bybit.com/v5/public/inverse",
        subscription={"op": "subscribe", "args": ["orderbook.50.BTCUSD"]},
    ),
    Feed(
        name="deribit",
        url="wss://www.deribit.com/ws/api/v2",
        subscription={
            "jsonrpc": "2.0",
            "id": 1,
            "method": "public/subscribe",
            "params": {"channels": ["book.BTC-PERPETUAL.none.20.100ms"]},
        },
    ),
)


def _read_exact(sock: ssl.SSLSocket, length: int) -> bytes:
    chunks: list[bytes] = []
    remaining = length
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise EOFError("websocket closed")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def encode_client_frame(payload: bytes, opcode: int = 0x1) -> bytes:
    """Encode one final, masked client frame."""
    if len(payload) > MAX_FRAME_BYTES:
        raise ValueError("payload exceeds capture bound")
    mask = os.urandom(4)
    first = 0x80 | opcode
    length = len(payload)
    if length < 126:
        header = bytes((first, 0x80 | length))
    elif length <= 0xFFFF:
        header = bytes((first, 0x80 | 126)) + struct.pack("!H", length)
    else:
        header = bytes((first, 0x80 | 127)) + struct.pack("!Q", length)
    masked = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
    return header + mask + masked


def decode_server_frame(sock: ssl.SSLSocket) -> tuple[bool, int, bytes]:
    """Decode one bounded server frame and return (final, opcode, payload)."""
    first, second = _read_exact(sock, 2)
    final = bool(first & 0x80)
    opcode = first & 0x0F
    masked = bool(second & 0x80)
    length = second & 0x7F
    if length == 126:
        length = struct.unpack("!H", _read_exact(sock, 2))[0]
    elif length == 127:
        length = struct.unpack("!Q", _read_exact(sock, 8))[0]
    if length > MAX_FRAME_BYTES:
        raise ValueError(f"server frame exceeds {MAX_FRAME_BYTES} bytes")
    mask = _read_exact(sock, 4) if masked else b""
    payload = _read_exact(sock, length)
    if masked:
        payload = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
    return final, opcode, payload


def _connect(url: str) -> ssl.SSLSocket:
    parsed = urlparse(url)
    if parsed.scheme != "wss" or not parsed.hostname:
        raise ValueError(f"unsupported URL: {url}")
    port = parsed.port or 443
    raw = socket.create_connection((parsed.hostname, port), timeout=10)
    sock = ssl.create_default_context().wrap_socket(raw, server_hostname=parsed.hostname)
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    path = parsed.path or "/"
    if parsed.query:
        path += "?" + parsed.query
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {parsed.hostname}:{port}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n\r\n"
    ).encode("ascii")
    sock.sendall(request)
    response = bytearray()
    while b"\r\n\r\n" not in response:
        response.extend(sock.recv(4096))
        if len(response) > 64 * 1024:
            raise ValueError("oversized websocket handshake")
    header = bytes(response).split(b"\r\n\r\n", 1)[0]
    if not header.startswith(b"HTTP/1.1 101"):
        raise ConnectionError(header.decode("ascii", errors="replace"))
    expected = base64.b64encode(
        hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode("ascii")).digest()
    )
    headers = header.lower()
    if b"sec-websocket-accept: " + expected.lower() not in headers:
        raise ConnectionError("invalid Sec-WebSocket-Accept")
    sock.settimeout(2)
    return sock


def _capture_feed(feed: Feed, deadline_ns: int, output: Path, errors: list[str]) -> None:
    fragments = bytearray()
    try:
        with _connect(feed.url) as sock, output.open("w", encoding="utf-8") as target:
            subscription = json.dumps(feed.subscription, separators=(",", ":")).encode("utf-8")
            sock.sendall(encode_client_frame(subscription))
            while time.monotonic_ns() < deadline_ns:
                try:
                    final, opcode, payload = decode_server_frame(sock)
                except TimeoutError:
                    continue
                receive_epoch_ns = time.time_ns()
                receive_mono_ns = time.monotonic_ns()
                if opcode == 0x8:
                    break
                if opcode == 0x9:
                    sock.sendall(encode_client_frame(payload, opcode=0xA))
                    continue
                if opcode == 0x1:
                    fragments = bytearray(payload)
                elif opcode == 0x0:
                    fragments.extend(payload)
                else:
                    continue
                if not final:
                    continue
                decoded = json.loads(fragments.decode("utf-8"))
                record = {
                    "source": feed.name,
                    "receiveEpochNanos": receive_epoch_ns,
                    "receiveMonoNanos": receive_mono_ns,
                    "payload": decoded,
                }
                target.write(json.dumps(record, separators=(",", ":"), sort_keys=True) + "\n")
                target.flush()
                fragments.clear()
    except Exception as exc:  # one feed failure must not hide the other feed's evidence
        errors.append(f"{feed.name}: {type(exc).__name__}: {exc}")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--duration-seconds", type=int, default=60)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    if args.duration_seconds < 1 or args.duration_seconds > 86_400:
        parser.error("duration must be between 1 and 86400 seconds")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    deadline_ns = time.monotonic_ns() + args.duration_seconds * 1_000_000_000
    errors: list[str] = []
    threads = []
    outputs = []
    for feed in FEEDS:
        output = args.output_dir / f"{feed.name}.jsonl"
        outputs.append(output)
        thread = threading.Thread(
            target=_capture_feed,
            args=(feed, deadline_ns, output, errors),
            name=f"capture-{feed.name}",
        )
        thread.start()
        threads.append(thread)
    for thread in threads:
        thread.join()
    manifest = {
        "captureDurationSeconds": args.duration_seconds,
        "completedEpochNanos": time.time_ns(),
        "feeds": [
            {
                "name": feed.name,
                "url": feed.url,
                "subscription": feed.subscription,
                "file": output.name,
                "sha256": _sha256(output) if output.exists() else None,
                "bytes": output.stat().st_size if output.exists() else 0,
            }
            for feed, output in zip(FEEDS, outputs, strict=True)
        ],
        "errors": errors,
    }
    (args.output_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    if errors:
        for error in errors:
            print(error)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
