# Feature: Phase 11 application assembly and operator control

The following plan implements `docs/implementation-plan.md` Phase 11 on the
fully merged Phase 10 mainline. It inherits the cell-first ownership, bounded
lanes, generation fencing, append-only journal, complete-only recovery, and
core-owned health decisions established by Phases 1–10.

## Feature Description

Assemble the existing market-data, strategy, risk, OEMS, journal, recovery, and
venue boundaries into a framework-free execution-cell control plane. Add a
bounded authenticated operator ingress, fail-closed startup/arming lifecycle,
generation-fenced configuration staging, watchdog/metrics snapshots, bounded
stage histograms, and ordered shutdown coordination.

## User Story

As an authorized operator, I want one explicit process lifecycle and a bounded
control interface so that the cell cannot arm before recovery evidence is
complete, every mutation is audited and replayable, and observability cannot
stall the trading core.

## Problem Statement

The repository contains the execution components but no production-shaped
assembly or authoritative control state. The current `CoreAgent` only drains
market-data lanes, while recovery, arming, operator commands, watchdogs,
configuration generations, metrics, and shutdown are not coordinated.

## Solution Statement

Add small explicit packages in `basis-app` for lifecycle, operator control,
configuration, observability, and assembly. Cold ingress authenticates and
authorizes immutable command data before publishing into a fixed SPSC lane. The
core processor rejects expiry, duplicates, stale generations, and unsafe arming;
audits accepted actions before applying them. A fixed result cache makes command
IDs idempotent. Lifecycle evidence gates arming, shutdown is ordered, metrics are
copied into preallocated snapshots, and watchdog/hedge-path decisions remain
core-owned. Extend the core duty cycle with quota-bounded priority/fairness
sources without reflection or synchronous callbacks to cold clients.

## Out of Scope / Non-Goals

- No live credential loading, actual exchange connection, or testnet order
  transmission; Phase 12 certifies the assembled runtime.
- No production TLS server or identity-provider integration. Phase 11 provides
  the bounded authenticated cold gateway seam and deterministic HMAC verifier.
- No auto-arm: restart always stops at `DISARMED_READY`.
- No replacement of Aeron Archive, venue adapters, or existing recovery logic.
- No Prometheus/OpenTelemetry dependency; exporters consume immutable primitive
  snapshots asynchronously.

## Feature Metadata

**Feature Type**: New capability / integration  
**Estimated Complexity**: Very high  
**Primary Systems Affected**: `basis-app`, core duty cycle, operator journal ingress  
**Dependencies**: Phases 3–10, Java 25, Agrona, SBE journal protocol

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 11  
**Back-references**: Phase 8 recovery, Phase 9 Bybit gateway, Phase 10 Deribit gateway  
**Forward-references**: Phase 12 end-to-end testnet certification

---

## CONTEXT REFERENCES

### Relevant Codebase Files

- `docs/implementation-plan.md:726` — normative Phase 11 scope and exit gate.
- `docs/component-design.md:700` — recovery/arming lifecycle.
- `docs/component-design.md:733` — operator control and ordered shutdown.
- `docs/component-design.md:758` — asynchronous observability contract.
- `basis-app/.../core/CoreAgent.java` — existing quota-bounded market-data loop.
- `basis-core/.../command/PriorityOrderCommandLane.java` — fixed SPSC lane pattern.
- `basis-core/.../risk/HedgePathHealth.java` — core-owned hysteretic path state.
- `basis-journal/.../recovery/RecoveryCoordinator.java` — complete startup evidence.
- `basis-journal/.../ingress/JournalIngress.java` — bounded audited publication.
- `basis-protocol/.../basis-messages.xml` — existing operator-control evidence.

### Patterns to Follow

- Every production package declares `@path` and `@owner` in `package-info.java`.
- Hot/core structures use fixed primitive arrays and caller-owned mutable views.
- Expected failures return enums rather than throwing.
- All time reads use injected `EpochClock`/`MonotonicClock`.
- Cold authentication may allocate, but secrets are defensive copies, redacted,
  and zeroized.
- Operator clients receive cached results asynchronously; the core never blocks
  on a response, exporter, filesystem, HTTP client, or journal consumer.

---

## IMPLEMENTATION PLAN

### Phase 1: Lifecycle and generation-fenced configuration

- Add execution-cell lifecycle states matching the recovery/startup and shutdown
  sequence.
- Gate arm on `DISARMED_READY`, complete reconciliation, warm books, healthy
  journal/path/watchdogs, and an active configuration generation.
- Add signed/versioned configuration envelopes with HMAC verification, staging,
  activation, and strictly increasing generations.

### Phase 2: Bounded authenticated operator control

- Add the complete command set: status, arm, disarm, kill, cancel-all,
  reconcile, snapshot, stage config, activate config, and shutdown.
- Add operator roles, request expiry, command IDs, expected generations, scope,
  and reason codes.
- Add fixed-capacity ingress and result lanes plus a bounded idempotency cache.
- Authenticate cold requests with constant-time HMAC comparison and authorize by
  role before ingress.
- Require successful audit publication before every state mutation.

### Phase 3: Core duty-cycle priority and fairness

- Add reusable bounded work-source seams and a duty-cycle scheduler.
- Drain health/watchdog checks first, urgent order work next, then operator and
  order facts, followed by round-robin market-data/normal/background work.
- Enforce per-source quotas and forced service intervals; expose oldest age and
  starvation counters.
- Preserve the existing `CoreAgent` constructor for compatibility and add the
  composed scheduler as the Phase 11 assembly path.

### Phase 4: Watchdogs, path health, histograms, and metrics

- Add a primitive watchdog sample/result covering core/event-loop progress,
  queue age/occupancy, stale data, time synchronization, disk, venue, and
  archive health.
- Feed core-owned `HedgePathHealth` with bounded stage-histogram percentiles and
  recovery hysteresis.
- Add fixed-bucket latency histograms and double-buffered primitive runtime
  snapshots.
- Export snapshots through a cold polling agent; exporter failure increments a
  counter and cannot feed decisions back into core state.

### Phase 5: Explicit assembly and ordered shutdown

- Add `ExecutionCellAssembly` with explicit constructor dependencies and no
  reflection/container framework.
- Coordinate configuration, archive, recovery, venues, books, warm-up,
  readiness, arming, drain, reconciliation, snapshot, journal flush, venue close,
  and infrastructure close.
- Add graceful and emergency shutdown runbooks with explicit deadline outcomes.

### Phase 6: Offline certification and documentation

- Test startup gates, stale/expired/duplicate commands, auth/role failures,
  audit-before-apply, slow/full result clients, queue fairness/starvation,
  watchdog degradation/recovery, and shutdown order/deadline behavior.
- Run focused, property, Python tooling, architecture, and full reactor gates.

## STEP-BY-STEP TASKS

1. **CREATE** lifecycle and configuration packages with generation/signature tests.
2. **CREATE** operator request/result models, fixed lanes, authenticator, processor,
   result cache, action ports, and audit seam.
3. **CREATE** priority/fair duty-cycle scheduler and queue-age/starvation tests.
4. **CREATE** watchdog, histogram, metrics snapshot/export packages and tests.
5. **CREATE** explicit execution-cell assembly and shutdown coordinator with
   deterministic lifecycle tests.
6. **UPDATE** operator/shutdown documentation and write an implementation report.
7. **RUN** formatting, focused app tests, property profile, tooling tests, full
   Java 25 reactor verification, architecture rules, and diff hygiene.

## TESTING STRATEGY

JUnit unit/component tests use injected clocks, fixed keys, fake audit/action
ports, and bounded lane capacities. Tests prove that no unsafe arm or unaudited
mutation succeeds, a duplicate command returns its original result, stale and
expired requests fail closed, urgent work cannot starve regular sources, slow
exporters cannot block core progress, and shutdown invokes dependencies in the
documented order. No test requires network, credentials, or disk outside JUnit
temporary directories.

## VALIDATION COMMANDS

1. `./mvnw -o spotless:apply`
2. `./mvnw -o -pl basis-app -am test`
3. `./mvnw -o -Pproperty-tests -pl basis-app -am verify`
4. `python3 -m unittest discover -s tools/tests -v`
5. `./mvnw -o -T1C clean verify`
6. `git diff --check`

## ACCEPTANCE CRITERIA

- [x] The cell cannot arm before complete recovery, warm-up, active config, and
  healthy journal/watchdog/hedge paths.
- [x] Every mutating operator command is authenticated, authorized,
  generation-fenced, audited before application, idempotent, and replayable.
- [x] Operator/result/metrics backpressure cannot block the core
  duty cycle.
- [x] Core priority quotas guarantee urgent latency while bounded fairness tests
  prove regular sources cannot starve.
- [x] Watchdog and latency evidence drive core-owned hysteretic health; exporters
  remain observational.
- [x] Graceful shutdown follows the documented order and deadline expiry produces
  an explicit emergency-required state.
- [x] Focused, property, architecture, and full reactor validation pass.

## OPEN QUESTIONS / ASSUMPTIONS

- HMAC authentication is an offline/runtime seam, not the final production
  identity provider; deployment TLS and identity integration require an
  operational decision before Phase 12.
- `status` is authorized and result-cached but does not require a critical audit;
  every mutation does.
- Existing SBE `OperatorControl` remains the durable control envelope; Phase 11
  uses stable application action codes in its reason/flags fields rather than
  breaking the schema before testnet wire certification.
- Venue/application construction is explicit but credentials remain absent, so
  offline tests use lifecycle ports and Phase 12 supplies live adapters.

## AMENDMENTS

- The existing SBE `ControlAction` enum was extended with additive values for
  every Phase 11 mutation so durable audit facts do not overload unrelated
  legacy action codes.
- Signed configuration decoding allocates only for the rare administrative
  stage operation; ordinary command, result, scheduler, watchdog, histogram,
  and metrics paths remain fixed-capacity and allocation-free after startup.
