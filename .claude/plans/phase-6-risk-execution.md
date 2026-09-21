# Feature: Phase 6 risk, execution groups, and OEMS state machine

This plan implements `docs/implementation-plan.md` Phase 6 on top of the Phase 5 opportunity engine. It covers the in-process, venue-neutral safety authority; private/order wire adapters, deterministic fake venues, journaling/recovery, and application assembly remain in their later declared phases.

## Feature Description

Build the single-writer pre-trade risk and OEMS core that turns a current opportunity into a worst-case reservation and bounded aggressive execution group. The core must keep every possible fill authorized, treat ambiguous outcomes as exposure, hedge actual incremental fills urgently, preserve reserved emergency capacity, and block initiation whenever kills, evidence, sessions, limits, rate state, or hedge-path health are unsafe.

## User Story

As the execution-cell owner, I want every order command to be created only through deterministic risk reservation and OEMS state machines so that fills, ambiguity, backpressure, and interleaved strategies cannot create unauthorized or untracked exposure.

## Problem Statement

Phase 5 can identify executable opportunities but cannot reserve exposure, enforce runtime limits, create child orders, process partial fills, represent UNKNOWN outcomes, prioritize hedges, or isolate concurrent strategy instances. Sending an opportunity directly to a venue would violate AC08–AC13, AC22, AC24, AC27, and AC29.

## Solution Statement

Add fixed-capacity HOT packages under `basis-core` for risk, OEMS, and order commands. Use struct-of-arrays tables with slot generations, bounded open-address identity indexes, partitioned token buckets, monotonic kill generations, hysteretic hedge-path health, and caller-owned mutable results. A venue-neutral aggressive execution engine will perform ordered pre-trade revalidation/reservation, allocate group and child state, publish normal IOC initiation, and publish hedge/cancel/unwind commands through a separate urgent lane.

## Out of Scope / Non-Goals

- No Bybit or Deribit private/order wire codec or authenticated socket; Phases 9–10 own them.
- No deterministic fake venue or scenario DSL; Phase 7 owns external venue simulation.
- No durable journal, snapshot, replay, or restart coordinator; Phase 8 owns persistence/recovery.
- No application lifecycle/control-plane assembly; Phase 11 owns process wiring and operator authorization.
- No passive maker, parallel IOC, TWAP, portfolio netting, or blind retry.

## Feature Metadata

**Feature Type**: New capability  
**Estimated Complexity**: High  
**Primary Systems Affected**: `basis-core`, `basis-benchmarks`, architecture tests  
**Dependencies**: Java 25 and existing test/JMH dependencies; no new library

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 6 / atomic task 11  
**Epic**: `docs/implementation-plan.md`

**Back-references**:

- `.claude/plans/phase-5-opportunity-engine.md` — complete opportunity/evidence source.
- `.claude/plans/phase-2-protocol-exact-domain.md` — compact IDs, clocks, and primitive deadlines.

**Forward-references**:

- Phase 7 will drive these state machines with deterministic fake venues.
- Phase 8 will journal and recover their facts and snapshots.
- Phases 9–10 will translate the command/fact contracts to venue wire protocols.

---

## CONTEXT REFERENCES

### Relevant Codebase Files

- `docs/architecture.md:407` — principal risk/OEMS entities, states, invariants, and ten-check order.
- `docs/component-design.md:550` — reservation lifecycle, kills, path health, SoA tables, and command lifecycle.
- `docs/implementation-plan.md:574` — Phase 6 tasks and exit criteria.
- `basis-core/.../identity/LocalOrderIdCodec.java` — deterministic session-fenced 128-bit child identity.
- `basis-core/.../deadline/PrimitiveDeadlineWheel.java` — bounded timer/slot-generation pattern.
- `basis-core/.../numeric/CheckedDecimalMath.java` — mandatory checked fixed-point arithmetic.
- `basis-strategy-api/.../pricing/MutableBasisOpportunity.java` — evidence that the application will copy into the primitive risk request.
- `basis-venue-api/.../lane/MarketDataLane.java` — fixed nonblocking lane and health-word pattern.
- `basis-app/.../architecture/ArchitectureRulesTest.java` — HOT/core dependency restrictions.

### New Files to Create

- `basis-core/.../risk/*` — envelopes, ordered request/decision engine, ledgers, reservation table, kill hierarchy, partitioned rate capacity, and hedge-path health.
- `basis-core/.../command/*` — primitive commands and separate fixed urgent/normal SPSC lanes.
- `basis-core/.../oems/*` — child/group states, tables, identity/execution dedupe indexes, aggressive coordinator, and mutable results.
- `basis-core/src/test/.../{risk,command,oems}` — transition, model/property, isolation, path-health, and burst-priority tests.
- `basis-benchmarks/.../RiskOemsBenchmark.java` — pre-trade/reservation and partial-fill/hedge discovery benchmarks.

### Patterns to Follow

- Final classes, primitive arrays, numeric enums, caller-owned results, constructor-time allocation only.
- Slot plus generation references; stale events never mutate reused slots.
- Expected rejection is a status/reason, not an exception.
- No collections, streams, clocks, logging, reflection, strings, or blocking in HOT production code.
- Single writer owns risk/OEMS mutation; command lanes are bounded SPSC boundaries.

---

## IMPLEMENTATION PLAN

### Phase 1: Safety primitives

- Implement monotonic hierarchical kills for global, venue, strategy, instrument, account, group, and session scopes.
- Implement partitioned normal/hedge/emergency token buckets with known/unknown venue feedback and hedge-token reservation.
- Implement hysteretic `HedgePathHealth` from lane age/occupancy/failures, order-agent progress, socket/session/rate state, UNKNOWN age, tail latency, and reconciliation.

### Phase 2: Ordered risk reservation

- Define immutable `RiskEnvelope`, mutable primitive `PreTradeRiskRequest`, stable reject reasons, and caller-owned decision/handle results.
- Implement strategy-generation-isolated ledgers and fixed reservation slots.
- Apply the architecture's ten checks in exact order and atomically reserve worst-case gross/net/unhedged/collateral plus hedge rate capacity.
- Revalidate Phase 5 evidence from current book epochs/sequences/receive times, local monotonic age/skew, configuration generation, and expiry.

### Phase 3: Command and OEMS state

- Implement fixed urgent and normal SPSC command lanes; urgent drain is always first and both lanes expose health/age/capacity.
- Implement child and execution-group SoA tables, primitive free lists, slot generations, deterministic order IDs, and bounded open-address identity/execution indexes.
- Encode explicit idempotent child/group transitions including UNKNOWN and RECONCILING; conflicting facts fault the group while duplicates do not double count.

### Phase 4: Aggressive execution policy

- Implement reservation-first IOC initiation with strict worst price.
- Hedge each incremental initiating fill, not the order/ack amount, using checked proportional rounding.
- Bound hedge attempts/pay-up, preserve explicit dust/imbalance, consume reserved hedge tokens, and use emergency unwind when urgent hedge publication fails.
- Retain maximum remaining exposure for ambiguous writes/timeouts/disconnects until authoritative reconciliation.

### Phase 5: Certification

- Add exhaustive transition tables and randomized conservation/idempotency tests.
- Prove interleaved strategy/generation isolation, fixed check ordering, kill monotonicity, rate partitioning, path-health hysteresis, urgent priority, and allocation-free steady state.
- Add JMH discovery, run every repository validation profile, review, report, and commit.

## STEP-BY-STEP TASKS

### CREATE risk safety primitives

- **IMPLEMENT**: kill hierarchy, partitioned buckets/table, path-health state/config/sample machine.
- **GOTCHA**: normal initiation never borrows hedge/emergency capacity; recovery requires consecutive healthy samples and reconciliation after uncertainty.
- **VALIDATE**: `./mvnw -o -pl basis-core test`
- **SATISFIES**: AC09, AC13, AC22, AC29.

### CREATE ordered risk engine and reservation tables

- **IMPLEMENT**: exact check order, current evidence comparison, bounded per-strategy ledger, reservation lifecycle and expiry-before-send.
- **GOTCHA**: once a send may have occurred, reservation cannot expire/release without terminal evidence or reconciliation.
- **VALIDATE**: focused risk tests plus property profile.
- **SATISFIES**: AC08, AC09, AC13, AC22, AC27, AC29.

### CREATE priority command lanes and OEMS tables

- **IMPLEMENT**: two SPSC lanes, child/group SoA, generation handles, ID indexes, idempotent facts, conflict-to-fault behavior.
- **GOTCHA**: acknowledgement is not fill; ambiguous write is UNKNOWN; stale slot generations are rejected.
- **VALIDATE**: transition/model tests and urgent burst tests.
- **SATISFIES**: AC10, AC11, AC13, AC24.

### CREATE aggressive execution coordinator

- **IMPLEMENT**: reserve → allocate → normal IOC, incremental fill → urgent hedge, bounded retry/pay-up/unwind, terminal release.
- **GOTCHA**: hedge quantities derive from confirmed fill increments and reservation remains conservative across UNKNOWN.
- **VALIDATE**: partial/full/duplicate/reordered/timeout/disconnect/backpressure tests.
- **SATISFIES**: AC08–AC13, AC22, AC24.

### CREATE properties, allocation gate, and benchmark

- **IMPLEMENT**: randomized conservation/idempotency/isolation properties, 100k steady-state allocation test, JMH fixtures.
- **VALIDATE**: all commands below.
- **SATISFIES**: Phase 6 exit gate.

## TESTING STRATEGY

### Unit and Model Tests

- Every risk rejection reason in fixed order, plus reservation consume/release/UNKNOWN behavior.
- Every valid child/group transition and invalid/conflicting transition.
- Duplicate and reordered writes, acks, executions, cumulative fills, cancels, and reconciliation.
- Partial initiating fill produces only its proportional urgent hedge.
- Kills, lane backpressure, session loss, timeout, and path-health failures stop initiation without suppressing risk reduction.

### Property Tests

- Random event sequences preserve `confirmed + maximum outstanding <= authorized`.
- Cumulative fills remain monotonic/bounded and duplicate execution identities do not alter totals.
- Interleaved strategies never mutate another slot/generation.

### Performance Tests

- 100,000 steady-state risk/OEMS operations after warm-up allocate less than one byte per operation.
- JMH discovers approved and rejected pre-trade paths plus partial-fill urgent hedge publication.

## VALIDATION COMMANDS

```bash
./mvnw -o spotless:check
./mvnw -o -T1C clean verify
./mvnw -o -Pproperty-tests -pl basis-core -am verify
./mvnw -o -Pbenchmark-tests -pl basis-core,basis-benchmarks -am test
python3 -m unittest discover -s tools/tests -v
```

## ACCEPTANCE CRITERIA

- [x] Worst-case confirmed plus possible outstanding exposure never exceeds the active envelope/reservation.
- [x] No initiation bypasses ordered evidence, limits, rate reservation, or HEALTHY hedge-path checks.
- [x] UNKNOWN and RECONCILING retain exposure and forbid blind retransmission.
- [x] Duplicate/reordered facts are idempotent; conflicting facts fault the affected group.
- [x] Actual incremental initiating fills publish proportionate urgent hedges within bounded imbalance/pay-up policy.
- [x] Kills/backpressure/session loss fail closed while emergency risk-reducing traffic retains capacity.
- [x] Strategies and configuration generations remain isolated under interleaved events.
- [x] Hot paths are fixed-capacity/allocation-free and all Java 25 gates pass.
