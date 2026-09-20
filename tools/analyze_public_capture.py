#!/usr/bin/env python3
"""Summarize receive cadence and latest-evidence skew from a capture run."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Iterable


def _percentile(values: list[int], percentile: float) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, math.ceil(percentile * len(ordered)) - 1)
    return ordered[index]


def _book_events(path: Path, source: str) -> list[dict[str, object]]:
    events = []
    with path.open(encoding="utf-8") as records:
        for line_number, line in enumerate(records, start=1):
            record = json.loads(line)
            payload = record.get("payload", {})
            if source == "bybit":
                is_book = str(payload.get("topic", "")).startswith("orderbook.")
            else:
                params = payload.get("params", {})
                is_book = (
                    payload.get("method") == "subscription"
                    and str(params.get("channel", "")).startswith("book.")
                )
            if is_book:
                events.append(
                    {
                        "source": source,
                        "line": line_number,
                        "mono": int(record["receiveMonoNanos"]),
                        "payload": payload,
                    }
                )
    return events


def _intervals(events: list[dict[str, object]]) -> list[int]:
    times = [int(event["mono"]) for event in events]
    return [current - previous for previous, current in zip(times, times[1:])]


def _summary(values: list[int]) -> dict[str, int | None]:
    return {
        "p50": _percentile(values, 0.50),
        "p90": _percentile(values, 0.90),
        "p99": _percentile(values, 0.99),
        "p999": _percentile(values, 0.999),
        "max": max(values) if values else None,
    }


def analyze(directory: Path) -> dict[str, object]:
    bybit = _book_events(directory / "bybit.jsonl", "bybit")
    deribit = _book_events(directory / "deribit.jsonl", "deribit")
    combined = sorted(bybit + deribit, key=lambda event: (int(event["mono"]), str(event["source"])))
    latest: dict[str, int] = {}
    skews = []
    for event in combined:
        latest[str(event["source"])] = int(event["mono"])
        if len(latest) == 2:
            skews.append(abs(latest["bybit"] - latest["deribit"]))

    bybit_types: dict[str, int] = {}
    for event in bybit:
        event_type = str(event["payload"].get("type", "unknown"))
        bybit_types[event_type] = bybit_types.get(event_type, 0) + 1

    deribit_depths = []
    for event in deribit:
        data = event["payload"]["params"]["data"]
        deribit_depths.append((len(data.get("bids", [])), len(data.get("asks", []))))

    return {
        "clockDomain": "single-process monotonic_ns",
        "durationNanos": (
            int(combined[-1]["mono"]) - int(combined[0]["mono"]) if len(combined) > 1 else 0
        ),
        "bybit": {
            "bookEventCount": len(bybit),
            "eventTypes": bybit_types,
            "receiveIntervalNanos": _summary(_intervals(bybit)),
        },
        "deribit": {
            "bookEventCount": len(deribit),
            "allNotificationsHaveDepth20PerSide": bool(deribit)
            and all(depth == (20, 20) for depth in deribit_depths),
            "receiveIntervalNanos": _summary(_intervals(deribit)),
        },
        "latestEvidenceSkewNanos": _summary(skews),
        "latestEvidenceSkewSampleCount": len(skews),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("capture_dir", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = json.dumps(analyze(args.capture_dir), indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
