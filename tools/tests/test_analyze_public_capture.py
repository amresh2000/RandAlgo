import json
import tempfile
import unittest
from pathlib import Path

from tools.analyze_public_capture import analyze


def write_records(path: Path, source: str, times: list[int]) -> None:
    records = []
    for index, mono in enumerate(times):
        if source == "bybit":
            payload = {"topic": "orderbook.50.BTCUSD", "type": "snapshot" if index == 0 else "delta"}
        else:
            payload = {
                "method": "subscription",
                "params": {
                    "channel": "book.BTC-PERPETUAL.none.20.100ms",
                    "data": {"bids": [[1, 1]] * 20, "asks": [[2, 1]] * 20},
                },
            }
        records.append(
            json.dumps(
                {
                    "source": source,
                    "receiveEpochNanos": mono + 1_000_000,
                    "receiveMonoNanos": mono,
                    "payload": payload,
                }
            )
        )
    path.write_text("\n".join(records) + "\n", encoding="utf-8")


class AnalyzeCaptureTest(unittest.TestCase):
    def test_reports_latest_evidence_skew_and_cadence(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            directory = Path(directory_name)
            write_records(directory / "bybit.jsonl", "bybit", [100, 200, 300])
            write_records(directory / "deribit.jsonl", "deribit", [150, 280])
            report = analyze(directory)
            self.assertEqual(report["bybit"]["bookEventCount"], 3)
            self.assertEqual(report["deribit"]["bookEventCount"], 2)
            self.assertTrue(report["deribit"]["allNotificationsHaveDepth20PerSide"])
            self.assertEqual(report["latestEvidenceSkewSampleCount"], 4)
            self.assertEqual(report["latestEvidenceSkewNanos"]["max"], 80)


if __name__ == "__main__":
    unittest.main()
