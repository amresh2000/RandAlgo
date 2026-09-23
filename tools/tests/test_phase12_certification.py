import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from tools.phase12_certification import evaluate, initialize, template, verify_manifest


class Phase12CertificationTest(unittest.TestCase):
    def test_template_contains_complete_fail_closed_matrix(self):
        result = template("4422a24")

        self.assertEqual(30, len(result["scenarios"]))
        self.assertEqual(22, len(result["acceptanceCriteria"]))
        self.assertTrue(all(item["status"] == "NOT_RUN" for item in result["scenarios"]))
        self.assertEqual([86400, 604800], [item["durationSeconds"] for item in result["soaks"]])

    def test_initialize_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "evidence.json"
            initialize(output, "4422a24")
            parsed = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual("basis-phase-12-certification/v1", parsed["schema"])
            with self.assertRaises(FileExistsError):
                initialize(output, "4422a24")

    def test_unexecuted_template_is_incomplete(self):
        decision, reasons = evaluate(template("4422a24"))

        self.assertEqual("INCOMPLETE", decision)
        self.assertIn("MISSING_SCENARIO_ACCEPTED_BYBIT", reasons)
        self.assertIn("MISSING_SOAK_7D", reasons)

    def test_incomplete_pass_evidence_fails_closed(self):
        document = template("4422a24")
        document["scenarios"][0]["status"] = "PASSED"

        decision, reasons = evaluate(document)

        self.assertEqual("FAILED", decision)
        self.assertIn("SCENARIO_EVIDENCE_INCOMPLETE_ACCEPTED_BYBIT", reasons)

    def test_invalid_identity_and_duplicate_fixture_fail(self):
        document = template("4422a24")
        document["schema"] = "unknown"
        document["buildRevision"] = "main"
        document["fixtures"].append(dict(document["fixtures"][0]))

        decision, reasons = evaluate(document)

        self.assertEqual("FAILED", decision)
        self.assertIn("INVALID_SCHEMA", reasons)
        self.assertIn("INVALID_BUILD_REVISION", reasons)
        self.assertIn("DUPLICATE_FIXTURE_BYBIT", reasons)

    def test_seven_day_soak_does_not_substitute_for_twenty_four_hour_run(self):
        document = template("4422a24")
        document["soaks"] = [document["soaks"][1]]

        _, reasons = evaluate(document)

        self.assertIn("MISSING_SOAK_24H", reasons)

    def test_manifest_verification_detects_changed_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = root / "frame.json"
            fixture.write_text("{}\n", encoding="utf-8")
            digest = hashlib.sha256(fixture.read_bytes()).hexdigest()
            manifest = root / "SHA256SUMS"
            manifest.write_text(f"{digest}  frame.json\n", encoding="utf-8")
            self.assertEqual([], verify_manifest(manifest))

            fixture.write_text("changed\n", encoding="utf-8")
            self.assertIn("hash mismatch", verify_manifest(manifest)[0])

    def test_manifest_verification_rejects_path_escape(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "SHA256SUMS"
            manifest.write_text(f"{'a' * 64}  ../secret\n", encoding="utf-8")

            failures = verify_manifest(manifest)

            self.assertTrue(any("unsafe path" in failure for failure in failures))


if __name__ == "__main__":
    unittest.main()
