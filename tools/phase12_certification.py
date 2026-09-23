#!/usr/bin/env python3
"""Initialize Phase 12 evidence and verify content-addressed wire fixtures."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

VENUE_SCENARIOS = (
    "ACCEPTED",
    "REJECTED",
    "PARTIAL_FILL",
    "FULL_FILL",
    "IOC_RESIDUAL",
    "CANCEL_RACE",
    "DUPLICATE",
    "LOST_ACK",
    "PRIVATE_DISCONNECT",
    "PUBLIC_GAP",
    "RATE_LIMIT",
)
CELL_SCENARIOS = (
    "PROCESS_KILL",
    "ARCHIVE_OUTAGE",
    "RESTART",
    "MANUAL_KILL",
    "FRESH_BOOKS_HIGH_SKEW",
    "URGENT_QUEUE_LATENCY",
    "ORDER_AGENT_LATENCY",
)
CRITERIA = tuple(f"AC{number:02d}" for number in range(1, 16)) + (
    "AC17",
    "AC23",
    "AC24",
    "AC25",
    "AC26",
    "AC27",
    "AC29",
)
HASH_PATTERN = re.compile(r"^[0-9a-f]{64}$")
PASSED = "PASSED"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def template(revision: str) -> dict[str, object]:
    scenarios = [
        _scenario(name, venue)
        for name in VENUE_SCENARIOS
        for venue in ("BYBIT", "DERIBIT")
    ]
    scenarios.append(_scenario("TOKEN_EXPIRY", "DERIBIT"))
    scenarios.extend(_scenario(name, "CELL") for name in CELL_SCENARIOS)
    return {
        "schema": "basis-phase-12-certification/v1",
        "buildRevision": revision,
        "testnetLimitationsDocumented": False,
        "scenarios": scenarios,
        "acceptanceCriteria": [
            {"criterion": criterion, "status": "NOT_RUN", "artifactSha256": []}
            for criterion in CRITERIA
        ],
        "soaks": [
            _soak(86_400),
            _soak(604_800),
        ],
        "fixtures": [
            {"venue": venue, "status": "NOT_RUN", "manifestSha256": ""}
            for venue in ("BYBIT", "DERIBIT")
        ],
        "findings": [],
    }


def _scenario(name: str, venue: str) -> dict[str, object]:
    return {
        "scenario": name,
        "venue": venue,
        "status": "NOT_RUN",
        "startedEpochMillis": 0,
        "completedEpochMillis": 0,
        "reasonCode": "NOT_RUN",
        "verified": {
            "positions": False,
            "balances": False,
            "fills": False,
            "fees": False,
            "openOrders": False,
            "reservations": False,
            "journal": False,
        },
        "artifactSha256": [],
    }


def _soak(duration: int) -> dict[str, object]:
    return {
        "durationSeconds": duration,
        "status": "NOT_RUN",
        "allocationAndJfr": False,
        "queue": False,
        "cpu": False,
        "memory": False,
        "reconnect": False,
        "latency": False,
        "artifactSha256": [],
    }


def initialize(output: Path, revision: str) -> int:
    if not re.fullmatch(r"[0-9a-f]{7,64}", revision):
        raise ValueError("revision must be a 7-64 character lowercase git object ID")
    if output.exists():
        raise FileExistsError(f"refusing to overwrite {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(template(revision), indent=2) + "\n", encoding="utf-8")
    print(output)
    return 0


def verify_manifest(manifest: Path) -> list[str]:
    failures: list[str] = []
    entries = 0
    manifest_parent = manifest.parent.resolve()
    for line_number, raw_line in enumerate(manifest.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(maxsplit=1)
        if len(parts) != 2 or not HASH_PATTERN.fullmatch(parts[0]):
            failures.append(f"{manifest}:{line_number}: malformed entry")
            continue
        expected, relative_name = parts
        relative_name = relative_name.removeprefix("*")
        relative_path = Path(relative_name)
        if relative_path.is_absolute() or ".." in relative_path.parts:
            failures.append(f"{manifest}:{line_number}: unsafe path {relative_name}")
            continue
        fixture = (manifest_parent / relative_path).resolve()
        if not fixture.is_relative_to(manifest_parent):
            failures.append(f"{manifest}:{line_number}: escaped path {relative_name}")
            continue
        entries += 1
        if not fixture.is_file():
            failures.append(f"{manifest}:{line_number}: missing {relative_name}")
        elif sha256(fixture) != expected:
            failures.append(f"{manifest}:{line_number}: hash mismatch {relative_name}")
    if entries == 0:
        failures.append(f"{manifest}: no fixture entries")
    return failures


def verify_fixtures(root: Path) -> int:
    fixture_root = root / "basis-sim" / "src" / "test" / "resources" / "wire"
    manifests = sorted(fixture_root.rglob("SHA256SUMS"))
    if not manifests:
        print(f"no SHA256SUMS files under {fixture_root}", file=sys.stderr)
        return 2
    failures = [failure for manifest in manifests for failure in verify_manifest(manifest)]
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    for manifest in manifests:
        print(f"verified {manifest.relative_to(root)} {sha256(manifest)}")
    return 0


def evaluate(document: dict[str, object]) -> tuple[str, list[str]]:
    """Evaluate an evidence document without trusting its claimed aggregate result."""
    reasons: list[str] = []
    failed = False
    if document.get("schema") != "basis-phase-12-certification/v1":
        reasons.append("INVALID_SCHEMA")
        failed = True
    if not re.fullmatch(r"[0-9a-f]{7,64}", str(document.get("buildRevision", ""))):
        reasons.append("INVALID_BUILD_REVISION")
        failed = True
    required_scenarios = {
        (name, venue) for name in VENUE_SCENARIOS for venue in ("BYBIT", "DERIBIT")
    }
    required_scenarios.add(("TOKEN_EXPIRY", "DERIBIT"))
    required_scenarios.update((name, "CELL") for name in CELL_SCENARIOS)
    observed: dict[tuple[str, str], dict[str, object]] = {}
    for item in document.get("scenarios", []):
        key = (item.get("scenario"), item.get("venue"))
        if key in observed:
            reasons.append(f"DUPLICATE_SCENARIO_{key[0]}_{key[1]}")
            failed = True
        observed[key] = item
    for name, venue in sorted(required_scenarios):
        item = observed.get((name, venue))
        if item is None or item.get("status") == "NOT_RUN":
            reasons.append(f"MISSING_SCENARIO_{name}_{venue}")
        elif item.get("status") != PASSED:
            reasons.append(f"SCENARIO_NOT_PASSED_{name}_{venue}")
            failed |= item.get("status") == "FAILED"
        elif not _scenario_complete(item):
            reasons.append(f"SCENARIO_EVIDENCE_INCOMPLETE_{name}_{venue}")
            failed = True

    criteria: dict[str, dict[str, object]] = {}
    for item in document.get("acceptanceCriteria", []):
        name = item.get("criterion")
        if name in criteria:
            reasons.append(f"DUPLICATE_CRITERION_{name}")
            failed = True
        criteria[name] = item
    for name in CRITERIA:
        item = criteria.get(name)
        if item is None or item.get("status") == "NOT_RUN":
            reasons.append(f"MISSING_CRITERION_{name}")
        elif item.get("status") != PASSED:
            reasons.append(f"CRITERION_NOT_PASSED_{name}")
            failed |= item.get("status") == "FAILED"
        elif not _valid_hashes(item.get("artifactSha256")):
            reasons.append(f"CRITERION_EVIDENCE_INCOMPLETE_{name}")
            failed = True

    soaks = document.get("soaks", [])
    for minimum, maximum, label in (
        (86_400, 604_800, "24H"),
        (604_800, sys.maxsize, "7D"),
    ):
        candidates = [
            item
            for item in soaks
            if minimum <= item.get("durationSeconds", 0) < maximum
        ]
        if len(candidates) > 1:
            reasons.append(f"DUPLICATE_SOAK_{label}")
            failed = True
            continue
        item = min(candidates, key=lambda value: value["durationSeconds"], default=None)
        if item is None or item.get("status") == "NOT_RUN":
            reasons.append(f"MISSING_SOAK_{label}")
        elif item.get("status") != PASSED:
            reasons.append(f"SOAK_NOT_PASSED_{label}")
            failed |= item.get("status") == "FAILED"
        elif not _soak_complete(item):
            reasons.append(f"SOAK_EVIDENCE_INCOMPLETE_{label}")
            failed = True

    fixtures: dict[str, dict[str, object]] = {}
    for item in document.get("fixtures", []):
        venue = item.get("venue")
        if venue in fixtures:
            reasons.append(f"DUPLICATE_FIXTURE_{venue}")
            failed = True
        fixtures[venue] = item
    for venue in ("BYBIT", "DERIBIT"):
        item = fixtures.get(venue)
        if item is None or item.get("status") == "NOT_RUN":
            reasons.append(f"MISSING_FIXTURE_{venue}")
        elif item.get("status") != PASSED:
            reasons.append(f"FIXTURE_NOT_PASSED_{venue}")
            failed |= item.get("status") == "FAILED"
        elif not HASH_PATTERN.fullmatch(str(item.get("manifestSha256", ""))):
            reasons.append(f"FIXTURE_EVIDENCE_INCOMPLETE_{venue}")
            failed = True

    for finding in document.get("findings", []):
        if not finding.get("resolved") and finding.get("severity") in (1, 2):
            reasons.append(f"UNRESOLVED_SEV{finding['severity']}_{finding.get('id', 'UNKNOWN')}")
            failed = True
    if not document.get("testnetLimitationsDocumented"):
        reasons.append("MISSING_TESTNET_LIMITATIONS")
    return ("CERTIFIED" if not reasons else "FAILED" if failed else "INCOMPLETE", reasons)


def _valid_hashes(value: object) -> bool:
    return isinstance(value, list) and bool(value) and all(
        isinstance(item, str) and HASH_PATTERN.fullmatch(item) for item in value
    )


def _scenario_complete(item: dict[str, object]) -> bool:
    verified = item.get("verified", {})
    return (
        isinstance(verified, dict)
        and isinstance(item.get("startedEpochMillis"), int)
        and item["startedEpochMillis"] > 0
        and isinstance(item.get("completedEpochMillis"), int)
        and item["completedEpochMillis"] >= item["startedEpochMillis"]
        and re.fullmatch(r"[A-Z0-9_]{1,64}", str(item.get("reasonCode", "")))
        and all(
            verified.get(name) is True
            for name in (
                "positions",
                "balances",
                "fills",
                "fees",
                "openOrders",
                "reservations",
                "journal",
            )
        )
        and _valid_hashes(item.get("artifactSha256"))
    )


def _soak_complete(item: dict[str, object]) -> bool:
    return all(
        item.get(name) is True
        for name in ("allocationAndJfr", "queue", "cpu", "memory", "reconnect", "latency")
    ) and _valid_hashes(item.get("artifactSha256"))


def validate(evidence: Path, output: Path | None) -> int:
    document = json.loads(evidence.read_text(encoding="utf-8"))
    decision, reasons = evaluate(document)
    report = dict(document)
    report["decision"] = decision
    report["reasonCodes"] = reasons
    serialized = json.dumps(report, indent=2) + "\n"
    if output is None:
        print(serialized, end="")
    else:
        if output.exists():
            raise FileExistsError(f"refusing to overwrite {output}")
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(serialized, encoding="utf-8")
        print(output)
    return 0 if decision == "CERTIFIED" else 1


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    commands = value.add_subparsers(dest="command", required=True)
    init = commands.add_parser("init", help="create a fail-closed evidence template")
    init.add_argument("--output", type=Path, required=True)
    init.add_argument("--revision", required=True)
    verify = commands.add_parser("verify-fixtures", help="verify checked-in fixture hashes")
    verify.add_argument("--root", type=Path, default=Path.cwd())
    hash_file = commands.add_parser("hash", help="hash one sanitized evidence artifact")
    hash_file.add_argument("--file", type=Path, required=True)
    validate_file = commands.add_parser("validate", help="evaluate a Phase 12 evidence file")
    validate_file.add_argument("--evidence", type=Path, required=True)
    validate_file.add_argument("--output", type=Path)
    return value


def main(arguments: list[str] | None = None) -> int:
    options = parser().parse_args(arguments)
    try:
        if options.command == "init":
            return initialize(options.output, options.revision)
        if options.command == "verify-fixtures":
            return verify_fixtures(options.root.resolve())
        if options.command == "hash":
            print(sha256(options.file))
            return 0
        if options.command == "validate":
            return validate(options.evidence, options.output)
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 2
    raise AssertionError("unreachable")


if __name__ == "__main__":
    raise SystemExit(main())
