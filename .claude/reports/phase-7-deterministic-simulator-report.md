# Phase 7 Deterministic Simulator — Execution Report

**Status:** COMPLETE  
**Branch:** `feature/phase-7-deterministic-simulator`  
**Base:** `3975998` (`feature/phase-6-risk-execution`)

## Delivered

- Added stable, venue-neutral order facts with explicit `SIMULATED`, `COUNTERFACTUAL`, and `ACTUAL` provenance and a bounded SPSC order-fact lane.
- Added the OEMS fact processor, idempotent child-order handling, conservative UNKNOWN behavior, definitive failure release, and deterministic state visitation seams.
- Added a dual-domain virtual clock, fixed-capacity deterministic scheduler, seeded fault stream, and stable equal-time ordering.
- Added offline Bybit and Deribit behavior profiles, bounded fake venue authority, conservative observed-depth paper fills, scripted failure modes, cancel, query, and authoritative reconciliation.
- Added a typed scenario builder and bounded runner supporting time advance, pumping, order-agent/fact/urgent-lane stalls, checkpoints, kills, archive failure, and hedge-path recovery controls.
- Added a versioned canonical SHA-256 digest over configuration, ordered command/fact streams, books, OEMS, risk, fake venue authority, scheduler state, and invariant outcomes.
- Added deterministic acceptance coverage for repeatability, paired execution, ambiguity, counterfactual isolation, partial hedging, duplicates/reordering, backpressure, stalls, kill/archive controls, and offline isolation.
- Added a hot-path allocation benchmark for the fact lane. It exposed and drove removal of per-drain callback and enum-array allocation.

## Plan Divergences

- Scenario definitions are code-native typed operational steps. Market books and venue fault plans are fixture inputs constructed before the run rather than textual scenario commands. This keeps the Phase 7 DSL dependency-free and type checked.
- Durable snapshots, journal replay, and real killed-process restart remain intentionally deferred to Phase 8. Phase 7 exercises the in-memory kill/disconnect/UNKNOWN/reconciliation boundaries only.
- Fake venues refuse to synthesize `ACTUAL` facts. The shared contract accepts `ACTUAL` so later authenticated gateways can use the same ingress path.

## Validation

All required checks passed on Java 25 in the isolated worktree:

- `./mvnw -o -pl basis-sim -am test` — PASS; simulator module 18 tests.
- `./mvnw -o -Pproperty-tests -pl basis-core,basis-venue-api,basis-sim -am verify` — PASS.
- `./mvnw -o spotless:check` — PASS.
- `./mvnw -o -T1C clean verify` — PASS across all 12 reactor modules.
- `python3 -m unittest discover -s tools/tests -v` — PASS; 4 tests.
- `./mvnw -o -Pbenchmark-tests -pl basis-venue-api -am test` — PASS; fact-lane allocation contract included.
- `git diff --check` — PASS.

## Remaining Boundary

Phase 8 must add durable journal/snapshot persistence, crash-consistent restart, and killed-process replay before the system can claim durable recovery. Authenticated Bybit/Deribit order gateways and application lifecycle assembly also remain outside this offline Phase 7 scope.
