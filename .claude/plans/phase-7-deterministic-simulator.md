# Feature: Phase 7 deterministic simulator and paper execution

This plan implements `docs/implementation-plan.md` Phase 7 on top of the Phase 6 risk and OEMS core. It creates an entirely offline execution laboratory that drives the same venue-neutral commands and facts as production, while leaving real authenticated venue gateways, durable recovery, and application lifecycle assembly to their declared later phases.

## Feature Description

Build virtual time, stable event scheduling, fake Bybit and Deribit execution venues, conservative paper fills, scenario fixtures, and canonical terminal digests. A scenario must be able to drive market data, risk, OEMS, command lanes, venue responses, fills, disconnects, backpressure, UNKNOWN outcomes, and reconciliation with no network, credentials, database, wall-clock dependency, or unprovable passive fill assumption.

## User Story

As the execution-cell owner, I want reproducible offline scenarios that exercise paired execution and every safety failure so that the same input, configuration, build identity, and seed always produce the same commands, state transitions, invariant report, and terminal digest before any live gateway is connected.

## Problem Statement

Phase 6 can reserve risk and coordinate aggressive orders, but it is currently called directly by unit tests. There is no shared normalized order-fact ingress, deterministic order-agent/venue loop, virtual scheduler, venue authority, scenario language, conservative fill model, or whole-system digest. Consequently AC10-AC14 cannot yet be exercised as end-to-end event sequences, and later private gateways would otherwise invent their own event contract.

## Solution Statement

First add a minimal primitive order-fact contract below the venue layer: normalized fact types live in `basis-core`, a bounded SPSC order-fact lane lives in `basis-venue-api`, and a single-writer OEMS fact processor resolves local IDs and applies write outcomes, acknowledgements, fills, terminal updates, and reconciliation. This placement avoids the forbidden `basis-core -> basis-venue-api` dependency while allowing fake and future real venue adapters to publish the same facts.

Then implement a fixed-capacity virtual scheduler in `basis-sim`, ordered by `(scheduledMonoNanos, sourcePriority, producerId, producerSequence)`, with an explicit build/config/input/seed run identity. Fake venues consume the real `PriorityOrderCommandLane`, apply scripted fault policies, maintain authoritative order state, and publish normalized facts. Paper execution only fills marketable aggressive quantity proven by a trusted observed book, subject to configured adverse delay/slippage, and labels every fact as `SIMULATED`, `COUNTERFACTUAL`, or `ACTUAL`.

A code-native scenario DSL assembles checked-in fixtures without adding a parser dependency. The runner advances only virtual time, runs to quiescence under explicit event/step bounds, evaluates invariants, and hashes canonical snapshots of books, orders, groups, positions, risk, emitted commands, venue state, scheduler counters, and failures.

## Recommended Direction

The recommended first slice is the normalized order-fact contract plus deterministic clock/scheduler. It is the narrow shared foundation: fake venues need it now, and Phases 9-10 can later translate Bybit/Deribit wire events into exactly the same fact path without coupling core OEMS to either adapter.

Use a Java fixture/builder DSL in Phase 7 rather than YAML/JSON. The scenarios are safety specifications that benefit from compile-time types, primitive values, and direct reuse of existing builders; a text schema and parser can be added later only if operators need author-authored scenarios.

## Out of Scope / Non-Goals

- No authenticated HTTP/private-WebSocket clients, request signing, or venue wire codecs; Phases 9-10 own them.
- No durable journal, snapshots, process restart replay, or crash-consistent persistence; Phase 8 owns them. Phase 7 simulates kill/disconnect/reconciliation inputs and verifies disarmed behavior, but does not claim durable recovery.
- No production process wiring, operator API, auto-arm behavior, or deployment; Phase 11 owns lifecycle assembly.
- No testnet certification; Phase 12 owns it.
- No passive-maker queue model, invented queue position, or fill inferred only from traded volume. Phase 7 proves only aggressive quantity available in an observed trusted book.
- No live shadow outcome retention/analytics; Phase 13 owns production shadow economics. Phase 7 only establishes event provenance and counterfactual isolation.
- No dependency from `basis-core` on `basis-venue-api`, concrete venues, transport, JSON, persistence, or wall clocks.

## Feature Metadata

**Feature Type**: New capability and integration harness  
**Estimated Complexity**: High  
**Primary Systems Affected**: `basis-core`, `basis-venue-api`, `basis-sim`, architecture tests  
**Dependencies**: Phase 6 command/risk/OEMS state; Java 25 and existing test dependencies; no new library

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 7 / atomic task 12  
**Satisfies**: AC10-AC14, with durable restart mechanics explicitly deferred to Phase 8  
**Architecture source**: `docs/component-design.md` section 16

**Back-references**:

- `.claude/plans/phase-6-risk-execution.md` — command lane, risk reservation, OEMS tables, UNKNOWN, reconciliation, and hedge-path health.
- `.claude/plans/phase-4-primitive-books.md` — trusted/fresh fixed-depth books used as fill evidence.
- `.claude/plans/phase-3-market-data-transport.md` — normalized market-data lanes and replay fixtures.

**Forward-references**:

- Phase 8 will persist/replay these normalized facts and add real killed-process recovery.
- Phases 9-10 will publish the same normalized facts from authenticated venue transports.
- Phase 11 will compose the production execution-cell lifecycle.

---

## CONTEXT REFERENCES

### Existing Contracts to Reuse

- `basis-core/.../time/MonotonicClock.java` and `EpochClock.java` — simulator clock implements both; production logic continues to receive interfaces.
- `basis-core/.../command/PriorityOrderCommandLane.java` — fake order agents drain the actual separate urgent/normal SPSC boundary.
- `basis-core/.../oems/AggressiveExecutionEngine.java` — paired initiation, incremental hedge, ambiguity, and emergency unwind authority.
- `basis-core/.../oems/ChildOrderTable.java` — local-ID/execution-ID dedupe and authoritative child state.
- `basis-core/.../oems/ExecutionGroupTable.java` — paired group/imbalance state.
- `basis-core/.../risk/{StrategyRiskLedger,RiskReservationTable,HedgePathHealth}.java` — exposure, reservations, UNKNOWN, and safe recovery.
- `basis-core/.../book/FixedDepthOrderBook.java` — trusted observed depth and conservative executable price.
- `basis-venue-api/.../lane/MarketDataLane.java` — bounded SPSC and health-word pattern for the order-fact lane.
- `basis-sim/.../capture/StableMarketDataDigest.java` — SHA-256 canonical encoding precedent, to be generalized rather than overloaded.
- `basis-app/.../architecture/ArchitectureRulesTest.java` — dependency direction and wall-clock restrictions.

### Existing Gaps to Close

- `ChildOrderTable` has an internal local-ID index but no public caller-owned resolution method; normalized facts cannot safely find a slot/generation yet.
- There is no normalized order/write/private fact model or bounded ingress lane.
- Phase 6 engine methods are role-specific and directly invoked; there is no single fact router that validates identity, generation, role, ordering, duplicates, and conflicts.
- OEMS/risk tables do not expose complete deterministic read-only visitation needed for a whole-system digest.
- `basis-sim` currently captures/replays market-data frames only; it has no virtual execution runtime.

### Dependency Placement

```text
basis-core
  order facts + OEMS fact processor + deterministic state visitation
      ^
      |
basis-venue-api
  bounded order-fact lane / producer contract
      ^
      |
basis-sim                 future basis-venue-bybit / basis-venue-deribit
  fake venues             real private/order adapters
```

`basis-core` must not import `basis-venue-api`; the existing module direction is the reverse. Concrete fake venues remain in `basis-sim` and use venue behavior profiles rather than introducing a main-scope dependency on the real adapters.

---

## IMPLEMENTATION PLAN

### Phase 1: Normalized order facts and OEMS ingress

- Add primitive fact/provenance enums and a caller-owned mutable fact under `basis-core.oems.fact`. Cover transport write success/failure/ambiguity, acknowledgement/working, incremental execution, cancel, reject, disconnect uncertainty, rate-limit feedback, and authoritative reconciliation.
- Keep command-response acceptance distinct from venue order acceptance, and keep acknowledgement distinct from fill.
- Add `OrderFactHandler` and an `OemsFactProcessor` that resolves the 128-bit local order ID, validates venue/instrument/role, and applies the appropriate Phase 6 transition exactly once.
- Extend `ChildOrderTable` with allocation-free caller-owned local-ID lookup returning slot plus generation. Do not expose mutable table internals.
- Publish follow-up urgent hedge/cancel/query commands through `PriorityOrderCommandLane`; if publication fails, preserve UNKNOWN/exposure and use existing fail-closed policy.
- Add `OrderFactLane` in `basis-venue-api.lane`, mirroring `MarketDataLane`: fixed power-of-two capacity, SPSC ownership, explicit offer result, independent health word, oldest-age/failure/occupancy telemetry, and no blocking.

### Phase 2: Virtual clock and stable scheduler

- Add `VirtualClock` implementing `EpochClock` and `MonotonicClock`, initialized with explicit epoch and monotonic origins. Time may only advance, and overflow/backward moves fail explicitly.
- Add a fixed-capacity primitive binary-heap scheduler. Each event has scheduled monotonic time, stable source priority, producer ID, producer sequence, event kind, and primitive payload/reference handle.
- Compare equal-time events by `(sourcePriority, producerId, producerSequence)` exactly as documented. Reject duplicate/nonmonotonic producer sequences and capacity exhaustion deterministically.
- Provide `runNext`, `runUntil`, and bounded `runToQuiescence(maxEvents, deadline)` operations. Never sleep, spawn racing workers, or consult JVM clocks.
- Add a deterministic seeded fault stream whose seed and draw count are included in the run report. Random draws choose among configured outcomes but never determine ordering implicitly.

### Phase 3: Fake venues and conservative paper execution

- Implement a common `FakeVenue` order-agent contract plus explicit Bybit and Deribit behavior profiles. Profiles model semantic differences (rate response, acknowledgement ordering, disconnect/reconciliation behavior) without importing their wire adapters.
- Drain urgent commands before normal commands using the production command lane, record every emitted command canonically, and schedule write/fact events through the virtual scheduler.
- Maintain a bounded authoritative fake-venue order store keyed by the full local ID. Support submit, cancel, query, reconciliation, duplicate command detection, and deterministic venue/execution identities.
- Implement configurable fixed/ranged latency, reject, delayed reject, disconnect at each lifecycle point, partial fill, duplicate, reorder, rate limit, accept-with-lost-response/UNKNOWN, and restart disagreement policies.
- Implement aggressive paper fills from a captured trusted-book snapshot at simulated arrival time. Fill only visible price levels within the order limit, never exceed observed quantity, and apply configured adverse latency/slippage. If evidence is stale, untrusted, absent, or insufficient, emit no unsupported fill.
- Define provenance behavior:
  - `SIMULATED` facts may drive a pure simulation run.
  - `COUNTERFACTUAL` facts are recorded in an isolated outcome stream and never mutate actual execution/risk state.
  - `ACTUAL` facts represent replayed normalized production evidence and are never synthesized by a fake venue.

### Phase 4: Scenario DSL and execution runner

- Add a code-native `ScenarioBuilder` that validates configuration and produces an immutable/data-only scenario before execution.
- Support steps for market-data image/update/reset, opportunity/start request, explicit time advance, feed delay/skew, venue fault policy, private disconnect/reconnect, order-agent stall/resume, lane stall/resume, kill, path-health sample/recovery, fact injection, reconciliation, and invariant checkpoint.
- Add a `SimulationRunner` that owns clock, scheduler, books, risk/OEMS state, command/fact lanes, fake venues, and recorders. Use a documented deterministic pump order at each virtual instant: due external inputs, health/timers, core/OEMS, urgent commands, normal commands, venue facts, then repeat until quiescent.
- Treat event/step/round bounds as configuration; exhaustion is an explicit failed run, not a hang.
- Add reusable paired-execution fixture builders so scenarios vary only their evidence, venue behavior, delays, and expected invariants.

### Phase 5: Canonical snapshots, digest, and invariant report

- Add bounded read-only visitation/snapshot APIs to child orders, groups, reservations, strategy ledger, and any command/book state missing complete accessors. Iterate by stable slot/level/key order and include slot generations where reuse affects meaning.
- Add a canonical binary digest encoder with explicit field tags/versions and byte order. Never hash enum ordinals without a stable wire code, object identity, hash-map iteration order, formatted text, absolute paths, or wall-clock values.
- Digest run identity (schema version, build identity supplied by the test/build, config hash, input hash, seed), terminal books, child orders, groups, positions, risk/reservations, fake-venue authority, scheduler/fault counters, emitted commands/facts, and invariant results.
- Return a structured `SimulationReport` containing digest, counts, terminal virtual time, first failure, invariant list, and provenance counts. Calling report/digest repeatedly must be idempotent.

### Phase 6: Acceptance and determinism certification

- Add golden scenarios for successful paired IOC, partial initiation with incremental hedge, reject, cancel, duplicate/reordered fact, conflicting duplicate, disconnect before/after acceptance, ambiguous write/UNKNOWN, authoritative reconciliation, and bounded emergency unwind.
- Add timing/path scenarios for clock-skew-of-arrival, asymmetric feed delay, stale/untrusted book, urgent-lane full/stall, order-agent stall, private-stream loss, rate limit, kill, and `HedgePathHealth` recovery hysteresis.
- Run every golden scenario twice in fresh runners and assert byte-identical event streams/reports/digests. Add generated seed/config matrices and assert safety invariants rather than a fixed digest across different seeds.
- Prove fake venues cannot produce passive/unobserved fills, counterfactual facts cannot mutate actual state, and UNKNOWN exposure is retained without blind retransmit.
- Add an offline isolation test/profile that supplies no credentials/database, rejects accidental sockets/HTTP clients by architecture dependency, and completes all scenarios without external resources.
- Keep Phase 6 allocation and architecture gates green; simulator orchestration is COLD, but the shared fact/OEMS/lane path remains fixed-capacity and allocation-free after construction.

---

## STEP-BY-STEP TASKS

### CREATE normalized order facts and bounded ingress

- **FILES**: `basis-core/src/main/java/.../oems/fact/*`, `basis-venue-api/src/main/java/.../lane/OrderFactLane.java`, focused tests.
- **IMPLEMENT**: stable numeric fact/provenance codes, primitive mutable envelope, handler, health/failure telemetry.
- **GOTCHA**: transport completion is not venue acceptance; facts must carry full local ID, source venue/session generation, receive epoch/monotonic times, and execution identity where applicable.
- **VALIDATE**: lane wrap/full/recovery tests, ownership checks, malformed fact rejection, steady-state allocation test.

### CREATE OEMS fact processor and lookup/visitation seams

- **FILES**: `basis-core/src/main/java/.../oems/OemsFactProcessor.java`, caller-owned lookup/snapshot views, table tests.
- **IMPLEMENT**: ID-to-slot/generation resolution; state/role-aware dispatch to Phase 6 engine/tables; duplicates, conflicts, UNKNOWN, and reconciliation.
- **GOTCHA**: reordered valid facts may be idempotent or safely held/rejected; conflicting execution identity must fault without double-counting; stale handles never mutate reused slots.
- **VALIDATE**: exhaustive transition/fact matrix plus randomized ordering/duplicate properties.

### CREATE virtual time and scheduler

- **FILES**: `basis-sim/src/main/java/.../{time,scheduler}/*` and tests.
- **IMPLEMENT**: dual-domain clock, fixed heap, stable comparator, sequence fencing, capacity/step bounds, deterministic seeded fault stream.
- **GOTCHA**: equal-time ordering is a simulation convention, not evidence of production causality; epoch and monotonic time remain distinct.
- **VALIDATE**: permutation/equal-time golden ordering, overflow/backward/capacity tests, repeated-run event-stream equality.

### CREATE fake venue authority and paper fill model

- **FILES**: `basis-sim/src/main/java/.../venue/*`, `.../paper/*`, venue/profile tests.
- **IMPLEMENT**: command consumption, bounded authority store, scheduled facts, fault plans, conservative depth walk, provenance isolation.
- **GOTCHA**: accept-with-lost-response creates UNKNOWN and query/reconciliation, never automatic resubmit; passive orders do not fill; book depth is captured at arrival, not retroactively.
- **VALIDATE**: fault matrix for both profiles and proof tests that every fill is bounded by visible marketable depth.

### CREATE scenario DSL, runner, and report

- **FILES**: `basis-sim/src/main/java/.../{scenario,runner,report}/*`, reusable test fixtures.
- **IMPLEMENT**: validated builder, deterministic pump, run bounds, checkpoints, canonical recorders, structured failures.
- **GOTCHA**: scenario declaration order must become explicit producer sequences; no outcome may depend on collection/hash iteration or test method order.
- **VALIDATE**: minimal run, full paired run, non-quiescent failure, invalid fixture, and counterfactual isolation tests.

### CREATE stable whole-system digest

- **FILES**: `basis-sim/src/main/java/.../digest/*`, state visitation extensions in `basis-core`, golden digest resources.
- **IMPLEMENT**: versioned canonical encoding of run identity, commands/facts, terminal books/OEMS/risk/venue state, counters, and invariant report.
- **GOTCHA**: build identity is explicit input; do not read Git, file timestamps, environment, paths, or JVM clocks during a run.
- **VALIDATE**: same inputs in fresh instances match; one meaningful field change changes digest; repeated report calls match.

### CREATE Phase 7 acceptance suite

- **FILES**: `basis-sim/src/test/java/.../acceptance/*`, checked-in golden expectations.
- **IMPLEMENT**: AC10-AC14 scenario matrix and documented Phase 8 boundary for true killed-process replay.
- **GOTCHA**: Phase 7 may prove post-kill disarmed/reconciliation behavior in memory, but must not mark durable AC14 recovery complete before Phase 8.
- **VALIDATE**: all commands below.

---

## TESTING STRATEGY

### Unit Tests

- Fact validation, lookup, state routing, duplicate/conflict behavior, and fact-lane capacity/health.
- Clock monotonicity, scheduler total ordering, sequence fences, seeded draws, and bounded termination.
- Fake venue command lifecycle and every configured fault at every relevant order state.
- Paper depth walking, partial quantity, adverse slippage, stale evidence, and no-queue-position behavior.
- Canonical encoding and deterministic snapshot visitation.

### Property and Model Tests

- Random duplicate/reorder/disconnect/fill sequences preserve `confirmed + possible outstanding <= authorized`.
- Every initiating fill increment is hedged at most once and never from acknowledgement quantity.
- Same scenario/config/build/seed produces the same ordered record and digest over repeated fresh runs.
- Counterfactual outcomes never change actual child/group/risk/position state.
- All runs either quiesce within bounds or return the same explicit failure.

### Integration Scenarios

- Paired execution across both Bybit/Deribit profile directions.
- Partial/full/reject/cancel/rate-limit/UNKNOWN/reconcile combinations.
- Feed skew/asymmetry, stale book, private loss, urgent backpressure, order-agent stall, kill, and path recovery.
- Imported normalized `ACTUAL` facts versus fake `SIMULATED` and isolated `COUNTERFACTUAL` facts.

## VALIDATION COMMANDS

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o -pl basis-sim -am test

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o -Pproperty-tests -pl basis-core,basis-venue-api,basis-sim -am verify

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o spotless:check

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o -T1C clean verify

python3 -m unittest discover -s tools/tests -v
```

## ACCEPTANCE CRITERIA

- [x] Identical schema/build identity/config/input/seed yields a byte-identical ordered record, invariant report, and terminal digest in fresh runners.
- [x] Equal virtual times use the documented stable ordering key, and no simulation code reads wall time or sleeps.
- [x] Fake Bybit and Deribit profiles support latency, reject, disconnect, partial fill, duplicate, reorder, rate limit, accept-with-lost-response, UNKNOWN, query, and reconciliation.
- [x] Paper fills are limited to trusted observed marketable depth at simulated arrival, apply configured adverse assumptions, and never claim passive queue position.
- [x] Every fact is explicitly `SIMULATED`, `COUNTERFACTUAL`, or `ACTUAL`; counterfactual facts cannot mutate actual state.
- [x] Duplicate/reordered facts are idempotent or safely conflict; no execution is counted twice.
- [x] Partial initiating fills create urgent hedges only for actual new fill increments and remain within maximum imbalance.
- [x] Market/private loss, backpressure, archive-equivalent scenario failure, rate limits, agent stalls, and kills fail closed deterministically.
- [x] UNKNOWN retains maximum possible exposure and cannot cause blind retransmission; authoritative reconciliation is required to clear uncertainty.
- [x] Terminal digest covers books, orders, groups, positions, risk/reservations, venue authority, emitted commands/facts, scheduler counters, and invariants in canonical order.
- [x] All acceptance scenarios run with no network, credentials, database, or real venue dependency.
- [x] Phase 7 tests document that durable killed-process replay remains open until Phase 8.

## RISKS AND MITIGATIONS

- **Shared fact contract becomes venue-shaped**: keep it lifecycle/identity/execution based, with stable primitive reason codes and bounded optional fields; wire-specific payload stays in adapters.
- **Simulator accidentally tests a separate path**: fake venues must drain the production command lane and publish through the shared fact lane/processor.
- **Digest masks nondeterminism**: assert the full canonical ordered record as well as its hash in focused tests.
- **Scenario pump invents causality**: document its total order, expose source priorities, and treat equal-time order only as a simulation rule.
- **AC14 overclaim before persistence**: certify uncertainty/disarm/reconciliation now, and reserve actual killed-process journal replay for Phase 8.
- **Overly optimistic paper fills**: require trusted arrival-time book evidence and explicit adverse assumptions; otherwise produce no fill.

## DEFINITION OF DONE

Phase 7 is complete when the offline acceptance suite drives the Phase 6 command/fact path through both fake venue profiles, all stated failure classes are reproducible, conservative paper outcomes have explicit provenance, complete terminal state is canonically digestible, repeated runs are identical, and the full Java 25 validation suite passes without external services.
