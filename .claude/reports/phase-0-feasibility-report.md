# Implementation Report — Phase 0 Feasibility

**Plan**: `docs/implementation-plan.md`  
**Branch**: `feature/phase-0-feasibility`  
**Status**: PARTIAL

## Summary

Established the Phase 0 decision and evidence framework, current public venue
capability contracts, representative inverse/linear/dated metadata fixtures,
and a dependency-free concurrent public-feed capture and analysis path. A live
smoke run proved both public WebSocket profiles and produced same-process
receive/skew evidence. Phase 0 cannot be complete until the predeclared
multi-day economic, entitlement, account, and candidate-region gates run.

## Tasks completed

- Cell-first v1 decision → `docs/adr/0001-cell-first-v1.md` (CREATE)
- Bybit public capability contract → `docs/venue-contracts/bybit-capabilities.md` (CREATE)
- Deribit public capability contract → `docs/venue-contracts/deribit-capabilities.md` (CREATE)
- Strategy definition/onboarding contract → `docs/strategy-onboarding.md` (CREATE)
- Reference economic gate → `docs/economic-gates/btcusd-btc-perpetual.md` (CREATE)
- Receive/stage latency contract → `docs/performance/latency-contract.md` (CREATE)
- Candidate-region experiment → `docs/deployment/candidate-region-report.md` (CREATE)
- Public capture procedure → `docs/venue-contracts/public-capture-procedure.md` (CREATE)
- Concurrent WebSocket capture tool → `tools/capture_public_feeds.py` (CREATE)
- Capture cadence/skew analyzer → `tools/analyze_public_capture.py` (CREATE)
- Six sanitized, checksummed metadata fixtures → `basis-sim/src/test/resources/wire/metadata` (CREATE)

## Tests added

- RFC 6455 client masking, extended server frame lengths, and oversize rejection.
- Deterministic same-clock capture cadence and latest-evidence skew analysis.
- Four unit tests pass with Python 3.14.6.
- Ten-second production public-feed smoke: 349 Bybit book events and 29 Deribit
  book notifications, zero capture errors. The raw JSONL evidence is intentionally
  gitignored; its checksummed manifest and diagnostic analysis remain local.

## Validation results

- `python3 -m unittest discover -s tools/tests -v` — PASS (4 tests)
- `python3 -m compileall -q tools` — PASS
- JSON fixture parsing with `jq -e` — PASS
- Metadata fixture checksum verification — PASS
- `git diff --check` — PASS

## Deviations from the plan

- The initial capture profile uses Deribit `100ms` because raw entitlement has
  not been confirmed. It is explicitly not treated as equivalent to raw.
- Region latency measurement and account/private behavior are not inferred from
  the developer workstation.
- Phase 1 Java/Maven setup has not begun because Phase 0 has not passed.

## Issues encountered

- Phase 0 requires at least seven representative production days and candidate
  region hosts; those observations cannot be compressed into a smoke run.
- Account-specific fee schedules, feed entitlements, and private/test order
  credentials remain unavailable.
- Current Deribit guidance says its legacy SBE feed is scheduled for deprecation
  at the end of 2026 in favor of selected-client Starbase. V1 remains on bounded
  JSON WebSocket and records this as a future constraint.
