# Feature: Greenfield Bybit/Deribit Basis OMS

This is the master implementation plan for the accepted
[architecture](architecture.md). It is intentionally ordered so that correctness,
recoverability, and economic evidence precede live capital and micro-optimization.
Every implementation task also inherits the normative per-component contracts
in [Component Design](component-design.md).

Before implementing a phase, revalidate venue documentation, dependency
versions, and the phase's assumptions. Each phase should be delivered from a
dedicated worktree and reviewable branch. Do not combine several phase gates in
one unreviewable change.

## Feature description

Create a Java 25, cell-first modular platform for onboarding and executing
cross-venue basis strategies between Bybit and Deribit. Supported perpetual and
dated-future pairs are described through versioned strategy definitions and
certified payoff, carry, signal, and execution models rather than hard-coded in
the OMS. Version one runs on a single host as one low-latency execution process
with asynchronous control, journal, replay, reconciliation, and observability.

## User story

As an algorithmic trading operator, I want to onboard and operate new
Bybit/Deribit basis strategies through a controlled definition and certification
process while bounding potential and realized leg risk, so that strategies can
be added without forking venue connectivity, risk, OEMS, or recovery logic.

## Problem statement

Two apparently similar exchange order books have different wire contracts,
quantity units, fees, funding, session semantics, and failure modes. A naive
implementation can trade stale data, mis-size inverse contracts, double-send
after a timeout, lose a fill, or block order entry behind persistence. The
system must make these differences explicit without putting distributed
services or databases in its critical path.

## Solution statement

Use venue-specific Netty WebSocket agents to decode normalized fixed-point
events into bounded Agrona SPSC lanes. A single core thread owns primitive array
books and invokes pricing, strategy, risk, and OEMS state machines directly.
Outbound commands go to dedicated venue order agents. Every fact is encoded as
SBE and recorded asynchronously through Aeron Archive. Deterministic replay,
fake venues, private-stream reconciliation, and fail-closed policies establish
safety before live capital is enabled. A strategy definition binds two venue
legs to reusable payoff, hedge-ratio, carry, signal, and execution-policy
implementations. Definition-only onboarding does not modify the execution
kernel.

## Out of scope

- Venues other than Bybit and Deribit.
- Spot and options until separately modeled and certified.
- Arbitrary runtime-loaded strategy bytecode or configuration that can bypass
  the common risk/OEMS path.
- Generic SOR/TWAP/VWAP/POV frameworks.
- Multi-region execution and cell-to-cell hedging in the v1 release.
- Kafka, NATS, Kubernetes, public APIs, and a trading GUI.
- Treasury transfers and automatic collateral rebalancing.
- Native Bybit MMWS/SBE and Deribit Starbase integrations without entitlement.
- Reusing the earlier Bitbucket implementations as source dependencies.

## Feature metadata

**Feature type:** New capability  
**Complexity:** Very high / safety-critical  
**Primary systems:** Market data, primitive books, pricing, strategy, pre-trade
risk, OEMS, venue gateways, journal/replay, reconciliation, operations  
**External dependencies:** Bybit V5 WebSocket/HTTP APIs, Deribit JSON-RPC
WebSocket/HTTP APIs, Java 25, Netty, Agrona, Aeron, SBE, Maven, JUnit, jqwik,
JMH, HdrHistogram, PostgreSQL for asynchronous projections

## Related work

**Architecture:** `docs/architecture.md`  
**Component contracts:** `docs/component-design.md`  
**Post-v1 execution extensions:** `docs/advanced-execution-design.md`  
**Research source:** *Designing a Multi-Venue, Multi-Region OMS for Low-Latency
Algorithmic Trading*  
**Reference-only repositories:** Bitbucket `messaging`, `market-data-feeder`,
and `oems`; do not inherit their APIs or implementations implicitly.

## Acceptance criteria

- **AC01:** A clean checkout builds and tests through the Maven wrapper on the
  pinned Java 25 toolchain without manually installed databases or missing data.
- **AC02:** Startup refuses to arm when live venue instrument metadata differs
  from any active strategy's certified product, tick, lot, multiplier,
  settlement, expiry, or status.
- **AC03:** Hot-path prices, amounts, fees, positions, and limits use exact
  scaled integers with checked overflow and explicit units.
- **AC04:** Recorded and generated market streams produce identical observable
  states in the primitive and slow reference books.
- **AC05:** A malformed, stale, crossed, disconnected, or semantically invalid
  book immediately revokes new-exposure permission.
- **AC06:** Pricing uses executable depth and includes fees, funding, slippage,
  liquidity haircut, latency risk, and safety reserve.
- **AC07:** Every execution opportunity carries the exact book sequences,
  timestamps, configuration generation, and cost decomposition used to decide.
- **AC08:** Confirmed fills plus maximum possible outstanding fills never exceed
  authorized exposure.
- **AC09:** Initiation cannot occur without hedge capacity, collateral, rate
  capacity, and a non-expired risk reservation.
- **AC10:** An ambiguous order outcome becomes `UNKNOWN`, reserves exposure, and
  cannot be blindly retransmitted.
- **AC11:** Duplicate/reordered acknowledgements, fills, order updates, and
  reconciliation responses are idempotent or produce a safe conflict state.
- **AC12:** Partial initiating fills create correctly sized hedges from actual
  fill increments and respect maximum imbalance.
- **AC13:** Market-data loss, private-stream loss, order-lane backpressure,
  archive failure, and operator kill all have deterministic fail-closed behavior.
- **AC14:** A killed process replays, reconciles both venues, and remains
  disarmed until all open/unknown exposure and books are resolved.
- **AC15:** Testnet certification covers create, partial fill, full fill, cancel,
  reject, duplicate, disconnect, timeout, restart, and reconciliation scenarios.
- **AC16:** Shadow mode records candidate decisions and counterfactual outcomes
  without transmitting orders.
- **AC17:** The core thread allocates zero bytes in steady state under the
  approved replay workload.
- **AC18:** On the target production host, event dequeue through book/decision/
  risk/enqueue meets p99 <= 50 microseconds and p99.9 <= 100 microseconds.
- **AC19:** No production credential is available to tests, developer defaults,
  logs, captures, heap dumps, or repository files.
- **AC20:** Mainnet activation requires explicit signed configuration, limits,
  runbook completion, and staged canary approval.
- **AC21:** A new strategy using already certified product/payoff, carry, signal,
  and execution models is onboarded by a versioned definition, fixtures,
  economic evidence, and certification without modifying venue sessions,
  books, risk, OEMS, journal, or recovery code.
- **AC22:** Multiple configured strategy instances remain isolated by ID,
  configuration generation, risk envelope, reservations, limits, positions,
  metrics, and kill scope while sharing canonical venue books safely.
- **AC23:** A failed ingress publication is observable through an independent
  lane-health word before new exposure; it invalidates the affected state and
  can never appear as ordinary feed silence.
- **AC24:** Under the certified burst workload, private fills and urgent hedge,
  cancel, or unwind commands meet their latency budget and are not starved by
  market data, timers, telemetry, or normal initiation.
- **AC25:** Journal high water stops initiation before critical reserve capacity
  is consumed; restart tests recover an asynchronously unrecorded crash tail by
  reconciling authoritative venue orders, fills, positions, and balances.
- **AC26:** Architecture tests prove owner-thread and hot/warm/cold dependency
  rules, and allocation profiling shows no steady-state core allocation.
- **AC27:** Every opportunity and pre-trade revalidation proves both per-leg age
  and cross-leg receive-time skew are inside the strategy/feed-profile limits
  using timestamps from the same process-local monotonic clock. Skew rejection
  does not falsely invalidate otherwise trustworthy individual books.
- **AC28:** A traceable stage-timestamp chain measures market-data receive to
  transport write and private-fill receive to hedge transport write. On the
  target host, both satisfy explicitly certified p99/p99.9 budgets; core-only
  latency cannot substitute for this gate.
- **AC29:** `HedgePathHealth` derives from urgent queue age/capacity, order-agent
  progress, socket/session/rate state, UNKNOWN age, and recent fill-to-write
  tails. Any state other than `HEALTHY` prevents new exposure while preserving
  risk-reducing traffic.

---

## Context references

### Repository files to read before implementation

- `docs/architecture.md` - Accepted boundaries, invariants, and decisions.
- `docs/component-design.md` - Normative ownership, data structures, lane
  topology, algorithms, failure behavior, performance rules, and review checklist.
- `docs/advanced-execution-design.md` - Post-v1 passive maker and regional-cell
  constraints; required reading before planning either extension.
- `README.md` - Golden-source precedence and repository status.
- The phase-specific ADR and venue contract fixture created in Phase 0.

There is no existing implementation to mirror. This is a greenfield repository;
new patterns must be introduced intentionally and documented.

### Primary external documentation

- [Bybit order book](https://bybit-exchange.github.io/docs/v5/websocket/public/orderbook)
  - Depth/push intervals, snapshot/delta rules, `u`, `seq`, and `cts`.
- [Bybit server-location FAQ](https://bybit-exchange.github.io/docs/faq)
  - Current documented AWS Singapore availability zones; revalidate before deployment.
- [Bybit order creation](https://bybit-exchange.github.io/docs/v5/order/create-order)
  - Asynchronous acknowledgement, IOC conversion, client ID, and `PostOnly` cancellation semantics.
- [Bybit WebSocket trade API](https://bybit-exchange.github.io/docs/v5/websocket/trade/guideline)
  - Authentication, unique request IDs, order commands, rate-limit response,
    and the rule that an acknowledgement is only acceptance.
- [Bybit fast executions](https://bybit-exchange.github.io/docs/v5/websocket/private/fast-execution)
  - Low-latency fill stream and its deliberately reduced field set.
- [Deribit bounded book](https://docs.deribit.com/subscriptions/orderbook/bookinstrument_namegroupdepthinterval)
  - `none` grouping, depth 20, raw entitlement, and native amount semantics.
- [Deribit full book](https://docs.deribit.com/subscriptions/orderbook/bookinstrument_nameinterval)
  - Snapshot/change and `change_id`/`prev_change_id` continuity contract.
- [Deribit market-data practices](https://docs.deribit.com/articles/market-data-collection-best-practices)
  - Separate market/order connections, raw entitlement, recovery, and location.
- [Deribit order practices](https://docs.deribit.com/articles/order-management-best-practices)
  - Queuing, pipelining, partial fills, edits, CoD, labels, and rate limits.
- [Aeron releases](https://github.com/aeron-io/aeron/releases)
  - Pin and review the selected client/driver/archive release.
- [Agrona `OneToOneRingBuffer`](https://github.com/aeron-io/agrona/blob/2.6.1/agrona/src/main/java/org/agrona/concurrent/ringbuffer/OneToOneRingBuffer.java)
  - Verify SPSC claim/write behavior, capacity requirements, and heartbeat API.
- [SBE tool guide](https://github.com/real-logic/simple-binary-encoding/wiki/Sbe-Tool-Guide)
  - Schema validation and Java codec generation.
- [SBE message versioning](https://github.com/aeron-io/simple-binary-encoding/wiki/Message-Versioning)
  - Append-only extension, `sinceVersion`, acting block length, and new-template rules.
- [Netty releases](https://netty.io/news/)
  - Use supported 4.2.x and review security fixes before every release.
- [Netty reference-counted objects](https://netty.io/wiki/reference-counted-objects.html)
  - Required ownership/release rules for direct `ByteBuf` parsing.

### Target repository structure

```text
basis-oms/
  pom.xml
  mvnw, mvnw.cmd, .mvn/wrapper/
  config/                      build quality rules
  deploy/                      versioned non-secret deployment profiles
  docs/
    advanced-execution-design.md
    architecture.md
    component-design.md
    implementation-plan.md
    adr/
    runbooks/
    venue-contracts/
  basis-protocol/              SBE schemas and generated codecs
  basis-core/                  domain, books, pricing primitives, risk, OEMS
  basis-strategy-api/          strategy definitions and zero-allocation model contracts
  basis-strategy-basis/        reusable two-leg basis signal/execution models
  basis-venue-api/             transport-neutral venue contracts
  basis-venue-bybit/           Bybit market/order/private adapters
  basis-venue-deribit/         Deribit market/order/private adapters
  basis-journal/               Aeron Archive, snapshots, replay/projection
  basis-sim/                   fake venues, capture/replay, scenario engine
  basis-app/                   process assembly and lifecycle
  basis-benchmarks/            JMH and end-to-end performance harness
```

### Planned source surfaces

These names establish ownership and dependencies. A phase may refine a name in
its ADR, but it must not collapse the boundaries.

- `basis-protocol/src/main/resources/sbe/basis.xml` - Canonical binary event and
  control schema.
- `basis-core/.../domain/InstrumentDefinition.java` - Immutable product units,
  scales, multipliers, trading rules, and lifecycle.
- `basis-core/.../domain/PayoffModel.java` - Exact native-to-canonical exposure,
  cash-flow, carry, margin, and PnL contract.
- `basis-core/.../math/ScaledDecimalParser.java` - Checked byte-to-long parser.
- `basis-core/.../time/CoreClocks.java` and `DeadlineWheel.java` - Injected
  monotonic/epoch clocks and bounded primitive timer scheduling.
- `basis-core/.../id/OrderId.java` and `VenueIdEncoder.java` - Canonical 128-bit
  identity plus bounded venue-specific ASCII encoding.
- `basis-core/.../book/FixedDepthOrderBook.java` - Primitive production book.
- `basis-core/.../book/BookTrustState.java` - Sync/freshness/epoch state.
- `basis-core/.../pricing/ExecutablePriceCalculator.java` - Depth-aware VWAP.
- `basis-core/.../pricing/BasisPricer.java` - Complete net-edge calculation.
- `basis-core/.../pricing/TemporalCoherenceGate.java` - Per-leg age and
  same-clock cross-leg receive-time-skew validation with explicit evidence.
- `basis-strategy-api/.../BasisStrategyDefinition.java` - Versioned two-leg
  onboarding contract and model IDs.
- `basis-strategy-api/.../StrategyModel.java` - Bounded hot-path strategy
  interface; no venue or transport types.
- `basis-strategy-basis/.../CrossVenueBasisStrategy.java` - Reusable opportunity
  lifecycle over a validated definition.
- `basis-strategy-basis/.../AggressiveHedgePolicy.java` - Initial reusable
  initiation/hedge policy.
- `deploy/strategies/schema.json` - Cold-path definition schema.
- `deploy/strategies/*.yaml` - Non-secret, versioned strategy instances.
- `basis-core/.../risk/PreTradeRiskEngine.java` - Ordered safety checks.
- `basis-core/.../risk/RiskEnvelope.java` - Pre-authorized bounded capacity.
- `basis-core/.../risk/HedgePathHealth.java` - Hysteretic urgent path/session/
  latency health gate that strategy code cannot override.
- `basis-core/.../oems/ChildOrderStateMachine.java` - Venue-neutral order truth.
- `basis-core/.../oems/ExecutionGroupStateMachine.java` - Two-leg exposure and
  hedge coordination.
- `basis-core/.../oems/ChildOrderTable.java` and `ExecutionGroupTable.java` -
  Fixed struct-of-arrays slots, generations, free lists, and identity indexes.
- `basis-venue-api/.../MarketDataSource.java` and `VenueOrderSession.java` -
  Primitive boundary contracts without Netty types.
- `basis-venue-api/.../LaneHealthWord.java` - Cache-line-isolated overflow and
  producer-generation health signal independent of the bounded data lane.
- `basis-venue-bybit/.../BybitMarketDataSession.java` and
  `BybitOrderSession.java` - Bybit-specific transport and codecs.
- `basis-venue-deribit/.../DeribitMarketDataSession.java` and
  `DeribitOrderSession.java` - Deribit-specific transport and codecs.
- `basis-journal/.../EventJournal.java`, `CoreSnapshotStore.java`, and
  `RecoveryCoordinator.java` - Archive, snapshot, replay, reconciliation gate.
- `basis-sim/.../DeterministicVenue.java` and `ReplayRunner.java` - Virtual
  venue and stable-digest replay.
- `basis-app/.../ExecutionCell.java` - Explicit process assembly and lifecycle.
- `basis-app/.../CoreAgent.java` - Bounded priority/fair duty cycle and idle
  strategy; the only entry point that mutates core-owned state.
- `basis-app/.../telemetry/LatencyTrace.java` - Preallocated stage timestamps
  and trace correlation from network receive/private fill through transport write.
- `basis-benchmarks/...` - JMH benchmarks separated from production artifacts.

### Patterns to establish

- Packages under `com.penguinsecure.basis`.
- Types use domain names; interfaces do not receive `I` prefixes.
- Hot-path commands/events are mutable flyweights or direct-buffer codecs owned
  by one thread. Long-lived configuration/state may use immutable records.
- Every numeric method makes units explicit in names (`priceTicks`,
  `notionalRiskCcy`, `quantityNative`, `underlyingDelta`) or in a
  zero-allocation domain wrapper proven by benchmarks.
- Expected venue/domain failures are numeric enums/results, not exceptions.
  Exceptions are reserved for invariant violations and move the cell to fault.
- No Lombok, reflection-based DI, runtime classpath scanning, or Java
  serialization.
- No dependency from `basis-core` to Netty, Aeron, JSON, JDBC, or venue modules.
- Strategy modules depend on `basis-strategy-api` and `basis-core`; core never
  depends on a concrete strategy. Registration is explicit in `basis-app`.
- No `BigDecimal`, streams, `Optional`, wall-clock lookup, locks, futures, text
  formatting, or unbounded collections in `basis-core` hot packages.
- All venue input is untrusted and bounded before parsing.

---

## Phase-by-phase low-latency decision map

This table is normative. A phase does not optimize later components early, but
it must preserve the constraints that make later optimization safe.

| Phase | Decision frozen in that phase | Explicitly avoided | Evidence required |
|---|---|---|---|
| 0 - Feasibility | Measure wire/economics, local receive-age/skew distributions, geography, and complete path latency before selecting the first strategy/profile | hard-coded BTC pair, assumed units/location, arbitrary skew/latency limits | captures, capability matrix, RTT/jitter, stage traces, predeclared economic gate |
| 1 - Foundation | Pin toolchain/dependencies and enforce hot-package rules in CI | floating dependency versions, framework-heavy bootstrap | reproducible clean build and architecture tests |
| 2 - Domain/protocol | Scaled integers, dense IDs, injected clocks, stage timestamps, temporal evidence, SBE evolution, bounded deadline wheel | hot `BigDecimal`, strings, wall clock, generic envelopes | oracle/property/compatibility tests |
| 3 - Feed transport | Netty event-loop parsing, one SPSC ring per lane, independent overflow health word, receive/decode/publish timestamps | JSON trees, shared MPSC ingress, unbounded frames, core-only timing | corpus/fuzz/allocation and scheduler fairness tests |
| 4 - Books | Preallocated best-first arrays; double-buffer complete images | radix/tree production book for bounded depth, silent repair | differential tests and captured-distribution JMH |
| 5 - Pricing/strategy | Immediate affected-strategy evaluation, same-clock per-leg age/skew gate, and caller-owned results | midpoint signals, individually-fresh-only checks, timer batching, floating-point authorization | economic oracle, skew boundary tests, full evidence reproduction |
| 6 - Risk/OEMS | Single-writer struct-of-arrays, worst-case reservation, urgent/normal lanes, hedge-path-health gate | direct strategy-to-venue calls, monitoring-only latency, blind retry, resizable maps | model/state-space, path-health, and burst-priority tests |
| 7 - Simulation | One deterministic scheduler and virtual clocks | sleep-based tests, optimistic queue-position fills | stable digest and seeded fault matrix |
| 8 - Journal/recovery | Asynchronous SBE/Aeron durability with critical reserve and venue reconciliation | database in hot path, false fsync guarantee, history edits | kill-point, crash-tail, corruption, rebuild tests |
| 9 - Bybit gateway | Separate trade/private sockets and execution-identity dedupe | treating command ack as fill, REST normal path | fake venue then scripted testnet contract suite |
| 10 - Deribit gateway | Pipelined correlated JSON-RPC and explicit session fencing | response-order assumptions, symbol-derived units | fake venue then scripted testnet contract suite |
| 11 - Assembly/control | Explicit constructors and bounded control lane; cold operator threads | reflection DI, synchronous core RPC | lifecycle, authorization, starvation, watchdog tests |
| 12 - Testnet | Validate stage tracing and path-health state transitions, not production latency values | extrapolating testnet liquidity or timing to mainnet | scenario matrix plus 24-hour/7-day soak |
| 13 - Shadow | Calibrate age/skew, latency risk, and economics on production training data; evaluate holdout | optimizing thresholds on holdout or gross spread | signed economic report and counterfactual tails |
| 14 - Host tuning | Choose GC/transport/affinity/queues/idle strategy and freeze end-to-end p99/p99.9 budgets from target-host tails | laptop or core-only benchmark claims, folklore tuning | reproducible stage breakdown and end-to-end gates |
| 15 - Canary | One bounded variable increase at a time with automatic disarm | broad launch, simultaneous strategy/notional/concurrency expansion | supervised fills, reconciliation, realized net P&L |
| 16 - Evolution | Follow the passive-maker/regional-cell contract and promote only from measured economics | changing IOC to PostOnly, calendar-driven dual cells, hidden portfolio netting | separate ADR/plan, shadow/canary evidence, partition/recovery certification |

---

# Implementation plan

## Phase 0 - Venue capability map, onboarding contract, and feasibility gate

**Purpose:** Remove product and wire assumptions before creating core APIs.

### Tasks

- Record ADR-0001 for the selected cell-first architecture and v1 scope.
- Build a Bybit/Deribit capability matrix for perpetual and dated futures by
  product family, underlying, quote/settlement currency, expiry, amount unit,
  market-data profile, order types, account mode, fee/funding source, and
  reconciliation interface.
- Query and store sanitized metadata fixtures for a representative inverse pair,
  linear pair, and dated-future pair where both venues list compatible legs.
- Draft the `BasisStrategyDefinition` schema and prove it can express those
  candidates without venue-specific fields leaking into strategy logic.
- Obtain raw-feed/account entitlements without storing credentials.
- Build a disposable capture harness or vetted command-line capture procedure
  for both public feeds; capture reconnects, duplicate snapshots, heartbeats,
  quiet periods, and volatility bursts.
- Capture process-local receive-time distributions for both legs concurrently;
  evaluate per-leg age and cross-leg skew by feed profile during calm and burst
  regimes before proposing thresholds.
- Write venue-contract documents stating which facts are documented, observed,
  inferred, or still unknown.
- Define each candidate's payoff/hedge normalization, executable-edge equation,
  target risk unit, conservative cost/carry/conversion model, opportunity
  duration, and go/no-go thresholds before evaluating data.
- Measure RTT/jitter and full receive/decode/core/queue/serialize/write stages
  from candidate hosts/regions to both venues. Revalidate venue geography and
  endpoint paths rather than treating documented locations as permanent.

### Exit gate

- At least one reference strategy passes the onboarding schema and economic
  gate; its choice is an output of Phase 0, not a platform constant.
- The product model can represent every product family deliberately admitted to
  the initial onboarding catalog without ambiguous units or rounding.
- Book semantics are sufficient to reconstruct trustworthy top-N state.
- At least one feed mode is available for each venue.
- A predeclared conservative economic analysis either passes or the project
  stops/pivots before building order execution.
- Candidate age/skew and end-to-end budgets are documented as hypotheses with a
  Phase 13/14 calibration method; no arbitrary universal threshold is embedded.

### Deliverables

- `docs/adr/0001-cell-first-v1.md`
- `docs/venue-contracts/bybit-capabilities.md`
- `docs/venue-contracts/deribit-capabilities.md`
- `docs/strategy-onboarding.md`
- `docs/economic-gates/<strategy-id>.md`
- `docs/performance/latency-contract.md`
- `docs/deployment/candidate-region-report.md`
- Sanitized, checksummed fixtures under `basis-sim/src/test/resources/wire/`

## Phase 1 - Reproducible engineering foundation

**Depends on:** Phase 0 scope acceptance.

### Tasks

- Create the Maven 3.9.16 wrapper, Java 25 toolchain, parent POM, modules, and a
  dependency BOM with no version declarations in child modules.
- Pin Aeron 1.53.2, Agrona 2.6.1, SBE 1.40.2, Netty 4.2.18.Final, JMH 1.37,
  JUnit 5.14.4, and jqwik 1.10.1 initially; run convergence and vulnerability
  checks and adjust only through a recorded dependency decision.
- Configure reproducible builds, UTF-8, compiler release 25, warnings as errors,
  formatting, static analysis, forbidden APIs, dependency convergence, and
  checksum verification.
- Add test categories: unit, property, integration, venue-contract, replay,
  chaos, and benchmark. Default `verify` must be hermetic.
- Add CI on a clean Linux runner for build/test/static checks and a separate
  dedicated performance runner that reports rather than hides regressions.
- Create architecture tests enforcing module direction and hot-package bans.
- Add `package-info.java` path classification/owner declarations and architecture
  tests that enforce the component-design hot/warm/cold rules.

### Exit gate

- `./mvnw -T1C clean verify` passes from a clean checkout without secrets,
  network, PostgreSQL, or local fixture files.
- Dependency tree is convergent and generated sources are reproducible.
- CI runs on every branch and stores test reports.

## Phase 2 - Protocol and exact numeric domain

**Depends on:** Phase 1.

### Tasks

- Define SBE message header fields: schema/version/template, event sequence,
  producer epoch, venue, account, instrument, exchange timestamp, local epoch
  receive timestamp, local monotonic timestamp, and configuration generation.
- Define fixed stage timestamps/trace identity and opportunity fields for both
  leg receive times/ages, feed profiles, calculated skew, and certified limits.
- Define messages for instrument definitions, book image/delta/trust, opportunity,
  risk decision, execution group, order command/state, fill, position, health,
  operator control, snapshot marker, and reconciliation result.
- Generate codecs during the build and enforce backward-compatible schema
  evolution rules in tests.
- Implement checked decimal-text-to-scaled-long parsing without intermediate
  strings; explicitly test scale, sign, overflow, zero, and malformed forms.
- Implement exact tick/lot/notional conversion plus certified linear and
  inverse payoff models, canonical exposure, hedge-ratio rounding, funding,
  expiry/settlement, and currency conversion primitives required by Phase 0.
- Implement immutable `InstrumentDefinition` and startup metadata comparison.
- Implement immutable `BasisStrategyDefinition` parsing/validation in the cold
  path, including model IDs, leg compatibility, account binding, limits,
  effective time, and configuration hash.
- Add slow BigDecimal test oracles outside hot packages.
- Implement injected epoch/monotonic clocks, canonical local IDs, bounded venue
  ID encoders, and a preallocated primitive deadline wheel.

### Exit gate

- Every admitted venue/product value has an exact unit/scale and round-trip test.
- Representative inverse, linear, and dated candidates either pass definition
  validation or fail with an explicit unsupported reason.
- Fuzz/property tests cannot cause silent overflow or risk-increasing rounding.
- SBE golden files decode across the current and previous schema version.

## Phase 3 - Market-data transport, capture, and codecs

**Depends on:** Phase 2 and validated Phase 0 wire fixtures.

### Tasks

- Define `MarketDataSource`, `MarketDataSink`, session state, health, reconnect,
  heartbeat, and subscription contracts in `basis-venue-api`.
- Implement bounded frame accumulation and explicit maximum frame sizes.
- Implement Bybit public linear/inverse WebSocket sessions selected from
  instrument metadata, with heartbeat, subscription union, reconnect, and a
  shared custom byte parser for approved bounded depths.
- Implement Deribit authenticated public WebSocket session, token refresh,
  heartbeat, subscription union, reconnect, and custom parser for approved
  bounded depth profiles.
- Preserve each venue's timestamps and sequence fields without translating them
  into a fake universal sequence.
- Record Netty receive-start, decode-complete, and ring-commit timestamps from
  the injected process-local clock without adding per-message allocation.
- Write normalized events to dedicated Agrona OneToOne rings with a documented
  market-data-full invalidation policy.
- Implement one ring per SPSC lane plus cache-line-padded `LaneHealthWord`
  overflow signaling; prove that a full data ring cannot hide loss from the core.
- Implement sanitized raw-frame capture and offline fixture replay on cold
  threads only.
- Fuzz both parsers and test fragmented/coalesced WebSocket frames, field order,
  unknown fields, missing fields, oversize messages, Unicode, and invalid numbers.
- Add a skeleton `CoreAgent` that samples lane-health words and drains multiple
  ingress lanes round-robin with bounded quotas; defer business dispatch until
  its owning component exists.

### Exit gate

- Production captures replay deterministically into the same normalized events.
- Parser hot paths allocate zero after warm-up.
- Disconnect, malformed input, and ring-full scenarios revoke book trust and
  cannot leak a plausible-looking event to the strategy.
- Burst tests prove a continuously busy or failed producer cannot hide the kill
  word, monopolize the core, or starve a private-result lane.

## Phase 4 - Primitive books and trust lifecycle

**Depends on:** Phase 3.

### Tasks

- Implement fixed-capacity best-first bid/ask arrays and primitive query APIs.
- Use two preallocated array sets for image feeds so validation completes before
  the active image swaps; delta feeds mutate only validated active state.
- Implement Bybit snapshot/delta application according to the captured contract.
- Implement Deribit bounded-image replacement; do not apply full-depth
  incremental assumptions to this channel.
- Add venue-specific sequence/epoch trackers and trust/freshness state machines.
- Add crossed-book, duplicate-level, invalid-tick, invalid-size, capacity, stale,
  and reconnect checks.
- Implement a slow TreeMap reference book in test support.
- Run differential and property tests after every generated/recorded event.
- Add JMH for snapshot, insert/update/delete, image replacement, best price,
  executable VWAP, and burst replay at representative depths.

### Exit gate

- AC04 and AC05 pass for fixtures, randomized streams, and gap/recovery cases.
- Primitive book is faster with lower tail variance than the reference and meets
  the internal Phase 4 budget; otherwise execute Spike S3 before continuing.

## Phase 5 - Strategy onboarding, pricing, carry, and opportunity engine

**Depends on:** Phase 4.

### Tasks

- Implement explicit strategy-model registration and validate definition model
  IDs at startup; no reflection or runtime bytecode loading.
- Implement executable VWAP and maximum executable canonical exposure over both
  legs through each leg's payoff and hedge-ratio model.
- Implement venue/account fee schedules with maker/taker distinction,
  provenance, effective time, and fail-closed expiry.
- Implement reusable funding, dated-expiry/settlement, and currency-conversion
  carry inputs with timestamps, confidence/provenance, and fail-closed expiry.
- Implement liquidity haircuts and latency-risk tables keyed by direction,
  venue pair, size bucket, volatility regime, and evidence age.
- Implement the reusable cross-venue basis model in both directions with a
  complete fixed-point cost decomposition and conservative rounding.
- Stamp opportunities with book epochs/sequences, configuration generation,
  both last-applied receive times/ages, cross-leg skew and limits, feed profiles,
  decision time, maximum notional, expiry, and all cost components.
- Implement `TemporalCoherenceGate`; reject a combined opportunity when either
  leg age or skew fails while retaining valid individual book trust.
- Add a slow BigDecimal oracle and property tests around break-even boundaries.
- Onboard the Phase 0 reference strategy plus at least two synthetic strategies
  using different supported product/payoff combinations. Prove no change is
  needed below the strategy/model layer.

### Exit gate

- No opportunity is emitted from untrusted/stale inputs or stale cost data.
- No opportunity is emitted from two individually fresh but temporally
  incoherent book views; boundary failures have a distinct reason code.
- Fast and slow models agree within the declared conservative rounding bound.
- Every decision is reproducible from its referenced inputs and configuration.
- AC21 passes for the reference and synthetic definitions.

## Phase 6 - Risk, execution groups, and OEMS state machine

**Depends on:** Phase 5.

### Tasks

- Implement kill hierarchy, `RiskEnvelope`, reservations, limits, daily loss,
  collateral, position, and rate-capacity checks in the fixed order from the
  architecture.
- Implement deterministic compact IDs containing strategy/session generation
  and monotonic sequence; preallocate venue string encoding buffers.
- Implement child-order and execution-group state machines with UNKNOWN and
  RECONCILING as first-class states.
- Track confirmed fills, possible outstanding fill, reserved notional,
  unhedged notional, fees, and positions in normalized and venue-native units.
- Implement preallocated per-strategy/group capacities plus aggressive
  initiation, incremental fill-driven hedge, bounded pay-up ladder, timeout,
  emergency unwind, and terminal rules. The first canary config limits each
  strategy to one group without hard-coding that restriction.
- Isolate reservations, positions, PnL, limits, metrics, and kill state by
  strategy ID and configuration generation while sharing read-only book state.
- Implement per-venue/account/operation token buckets with permanently reserved
  cancel and emergency-hedge capacity.
- Implement fixed struct-of-arrays slot tables, slot generations, bounded
  open-address identity indexes, and separate urgent/normal outbound lanes.
- Implement hysteretic `HedgePathHealth` from urgent queue age/occupancy,
  publication failures, order-agent progress, socket/session/rate state,
  UNKNOWN age, and recent fill-to-write latency. Non-healthy blocks initiation.
- Reject invalid transitions and conflicting duplicate events by faulting the
  affected scope, not by silently coercing state.
- Add model-based/property tests for every transition and invariant.

### Exit gate

- AC08 through AC13 hold across exhaustive small-state model tests and randomized
  duplicate, delay, reorder, partial-fill, timeout, and disconnect scenarios.
- No strategy code can call a venue gateway without passing the safety layer.
- AC22 holds under interleaved events from multiple strategy instances.
- AC27 and AC29 hold under skew boundary, timer, order-agent stall, socket
  degradation, rate exhaustion, and recovery scenarios.

## Phase 7 - Deterministic simulator and paper execution

**Depends on:** Phase 6.  
**Independent of:** Real private/order gateway implementation in Phases 9-10.

### Tasks

- Implement virtual clock and deterministic scheduler; production code receives
  clock interfaces rather than calling wall time directly.
- Implement fake Bybit and Deribit venues with configurable latency, reject,
  disconnect, partial fill, duplicate, reorder, rate limit, and unknown outcomes.
- Implement conservative paper fills based on order type and observed book;
  explicitly label simulated, counterfactual, and actual events.
- Build scenario DSL/fixtures for paired execution and failure cases.
- Add deterministic clock-skew-of-arrival, asymmetric feed delay, urgent-queue
  stall, order-agent stall, and hedge-path-health recovery scenarios.
- Make replay produce a stable digest of terminal books, orders, groups,
  positions, risk, and emitted commands.

### Exit gate

- Identical input/configuration/build produces an identical digest.
- Every acceptance scenario can run without network, credentials, or databases.
- The simulator cannot claim queue position or fills that its model cannot prove.

## Phase 8 - Journal, snapshots, replay, and recovery

**Depends on:** Phase 2 event schemas and Phase 6 state.

### Tasks

- Configure embedded or sidecar Aeron Media Driver and Archive with explicit
  directories, segment sizing, retention, storage alarms, and nonblocking
  publication.
- Encode and record normalized inputs, decisions, commands, venue events, state
  transitions, operator actions, and fault events.
- Implement versioned core snapshots with checksums and atomic publication.
- Implement snapshot-load plus journal-replay and schema-version handling.
- Implement restart coordinator that remains disarmed until both venue
  reconcilers and both market books are trustworthy.
- Implement asynchronous PostgreSQL projector with idempotent event keys;
  projection failure must not mutate or block the core.
- Test archive unavailable/backpressured/full, corrupt/truncated snapshots,
  duplicate replay, crash points, and venue/journal disagreement.
- Classify journal facts as critical, important, or lossy; size and test a
  critical reserve that covers all terminal/risk-reducing transitions for the
  configured maximum outstanding exposure after initiation stops.

### Exit gate

- Kill-at-every-transition tests converge to safe reconstructed state.
- Journal backpressure stops initiation while allowing reserved risk-reducing
  actions.
- Crash-tail tests prove that venue reconciliation emits compensating facts for
  execution missing from the last durable archive position.
- PostgreSQL can be deleted and rebuilt solely from retained events/snapshots.

## Phase 9 - Bybit private stream and order gateway

**Depends on:** Phases 6 and 7; integrate with Phase 8 before certification.

### Tasks

- Implement separate private and WebSocket trade connections with authentication,
  heartbeat, reconnect, bounded frames, and credential redaction.
- Encode create/cancel requests with deterministic unique request and order-link
  IDs, timestamps, receive windows, native amount, price, TIF, and reduce-only.
- Treat trade-command responses as acceptance only; resolve state from private
  order and execution streams.
- Combine fast execution notifications with full execution/order data without
  double-counting; key fills by venue execution identity.
- Implement open-order, history, execution, position, balance, instrument, fee,
  funding, and server-time reconciliation via rate-limited HTTP fallback.
- Implement rate-limit feedback, session generation, cancel-on-disconnect where
  supported/tested, mass cancel, and operator kill behavior.
- Add golden request/response tests and a scripted testnet certification suite.
- Record private receive/decode/ring stages and order dequeue/serialization/
  write/transport-completion stages with trace correlation.

### Exit gate

- Every Bybit order outcome, including lost ack and reconnect, resolves to an
  authoritative state or remains safely UNKNOWN without blind retransmission.

## Phase 10 - Deribit private stream and order gateway

**Depends on:** Phases 6 and 7; integrate with Phase 8 before certification.

### Tasks

- Implement a dedicated authenticated JSON-RPC WebSocket for order entry and a
  private subscription path for orders, trades, portfolio, and position data.
- Pipeline requests with monotonic JSON-RPC IDs and correlate out-of-order
  responses without making response order a state assumption.
- Encode buy/sell/edit/cancel/mass-cancel with exact product-native amounts,
  labels, TIF, post-only/reject behavior, and reduce-only.
- Deduplicate trade IDs in their documented currency scope and correlate order,
  trade, and label identities.
- Implement token refresh, heartbeat/test requests, cancel-on-disconnect with
  verified session semantics, rate credits, and reconnect fencing.
- Implement open order/history/trade/position/balance/instrument/fee/funding
  reconciliation without scanning unbounded history on the hot path.
- Add golden tests and a scripted testnet certification suite.
- Record the same private receive through transport-write stages as Bybit so
  venue paths are comparable without conflating their protocol semantics.

### Exit gate

- Every Deribit order outcome meets the same UNKNOWN/reconciliation standard as
  Bybit and amount conversion is exact for all admitted orders.

## Phase 11 - Application assembly and operator control

**Depends on:** Phases 3-10.

### Tasks

- Assemble explicit constructors in `basis-app`; do not add a reflection-based
  application framework to the execution process.
- Define lifecycle order for configuration, archive, venue metadata, private
  reconciliation, market-data sync, warm-up, arm, drain, and shutdown.
- Implement bounded, authenticated operator commands over a cold interface:
  status, arm, disarm, kill, cancel-all, reconcile, snapshot, and config stage.
- Validate signed/versioned configuration and require generation checks on all
  updates consumed by the core.
- Export asynchronous health, book trust, session, risk, exposure, rate, archive,
  latency, allocation, and decision metrics.
- Implement watchdogs for core progress, event-loop progress, queue occupancy,
  stale data, time synchronization, disk capacity, and venue degradation.
- Aggregate stage histograms and publish core-owned `HedgePathHealth` transitions
  with hysteresis and recovery windows; metrics exporters do not decide health.
- Add graceful drain plus forced emergency shutdown runbooks.
- Implement the bounded core duty-cycle priority and fairness policy from
  Component Design section 8; add queue-age and starvation tests.

### Exit gate

- The process cannot arm before complete startup reconciliation and warm-up.
- Every operator action is authenticated, authorized, journaled, and replayable.
- A slow metrics or operator client cannot affect core latency.

## Phase 12 - End-to-end testnet certification

**Depends on:** Phase 11.

### Tasks

- Run both venues continuously on testnet with separate nonproduction accounts.
- Execute the certification matrix: accepted, rejected, partial/full fill, IOC
  residual, cancel race, duplicate, lost ack, private disconnect, public gap,
  token expiry, rate limit, process kill, archive outage, restart, and manual kill.
- Add two-fresh-books/high-skew rejection plus injected urgent-queue/order-agent
  latency that must drive `HEALTHY -> DEGRADED/UNSAFE` and suppress initiation.
- Verify positions, balances, fills, fees, open orders, reservations, and journal
  after every scenario.
- Run 24-hour then 7-day soak tests with allocation/JFR, queue, CPU, memory,
  reconnect, and latency reports.
- Freeze approved wire fixtures and update venue-contract documents.

### Exit gate

- AC01-AC15, AC17, AC23-AC27, and AC29 hold; no unresolved severity-1/2 finding exists.
- Testnet limitations are explicitly documented rather than extrapolated to
  mainnet.

## Phase 13 - Production shadow and economic gate

**Depends on:** Phase 12.

### Tasks

- Connect to production public data and production read-only private/account
  state while hard-disabling order transmission.
- Record opportunities, simulated actions, realized subsequent prices, funding,
  fees, opportunity durations, and fill-to-hedge counterfactual distributions.
- Record per-leg age/skew distributions and relate rejected/accepted opportunities
  to subsequent convergence, false-edge rate, and executable net P&L.
- Run at least seven representative days including active and quiet regimes;
  extend the sample if it does not contain volatility bursts.
- Calibrate haircut, latency risk, minimum edge, expiry, notional buckets, and
  pay-up plus age/skew/path-health thresholds only on training windows; report
  holdout results separately.
- Compare candidate hosting regions and choose from measured latency/jitter and
  operational reliability.

### Exit gate

- AC16 passes and the predeclared economic gate remains positive on holdout data.
- If it fails, stop. Do not compensate by relaxing safety margins until backtest
  P&L looks attractive.

## Phase 14 - Production performance and operational hardening

**Depends on:** Target host selection in Phase 13.

### Tasks

- Execute Spike S4 for epoll/io_uring, G1/ZGC, heap/direct memory, ring sizes,
  fragment limits, idle strategies, affinity, IRQ/RSS, NUMA, and power settings.
- Run burst replay above observed peak, long soak, chaos, disk pressure, network
  impairment, and restart tests on production-equivalent hardware.
- Produce the complete stage breakdown and freeze per-profile p99/p99.9 gates
  for market-data-to-transport-write and private-fill-to-hedge-write. Demonstrate
  that induced breaches drive hedge-path health unsafe before further initiation.
- Establish signed artifact/config promotion, SBOM, dependency/security scan,
  secret injection, log/capture redaction, backups, restore, and rollback.
- Finalize dashboards, paging thresholds, venue incident procedures, exposure
  break-glass procedures, and daily reconciliation.
- Conduct an independent architecture/code/risk review and close all critical
  findings.

### Exit gate

- AC17-AC20 and AC27-AC29 pass on the actual deployment profile.
- A human operator completes a rehearsal of kill, restart, reconcile, rollback,
  and credential revocation using the runbooks.

## Phase 15 - Mainnet canary and controlled scale-up

**Depends on:** Phase 14 and explicit capital authorization.

### Tasks

- Provision dedicated subaccounts, withdrawal-disabled/IP-restricted keys, and
  the smallest practical collateral.
- Start disarmed; reconcile; arm with one execution group, minimum size, strict
  daily loss, strict maximum imbalance, and low order-rate limits.
- Require supervised windows and review every decision, order, fill, fee,
  funding event, residual, and reconciliation difference.
- Increase only one dimension at a time: observation time, notional, concurrency,
  instrument count, or strategy complexity.
- Automatically return to disarmed on any invariant breach, unknown-state age,
  reconciliation difference, stale/skewed evidence, hedge-path degradation,
  archive health failure, or loss limit.

### Exit gate

- Stable operation and positive realized net economics across the predeclared
  sample; no scale increase is justified by gross spread alone.

## Phase 16 - Post-v1 evolution

**Not part of the initial live release.** Additional strategies using certified
models follow the onboarding gate; changes to models, policies, venues, or
deployment boundaries require their own implementation plan and, where noted,
an ADR.

- Additional Bybit/Deribit underlyings and pairs through the standard onboarding
  gate; these are normal extensions, not architecture changes.
- Portfolio-level netting across independently limited strategies.
- `PassiveMakerAggressiveHedgePolicy` only after the quote-economics,
  post-only/edit semantics, cancel races, full resting-fill reservations,
  queue/adverse-selection model, hedge-path-health, shadow, and minimum-size
  canary gates in [Advanced Execution Design](advanced-execution-design.md).
- Spot/options or any payoff family not certified in the initial catalog.
- Regional control plane plus fenced venue-proximate cells only if measured
  single-cell economics justify the added distributed-state risk.
- Maker-side execution-group authority and an idempotent cell-to-cell canonical
  hedge-requirement protocol; hedge-side cells select native orders from fresh
  local state and reconcile cumulative residual delta.
- Replicated archive/global parent OMS and treasury rebalancer.
- Entitled Bybit MMWS/SBE and Deribit Starbase adapters.

---

# Atomic task sequence

Each phase above should normally become an epic or milestone. Within it, execute
the following dependency spine in order; split each numbered item into a small
reviewable ticket with its own tests.

1. **CREATE** Phase 0 ADRs, venue contracts, captures, and economic gate.  
   **Validate:** independent review of units, receive age/skew, stage latency,
   geography assumptions, and go/no-go rule.  
   **Satisfies:** AC02, foundation for AC06, AC27-AC29.
2. **CREATE** root Maven wrapper/BOM/modules/quality gates.  
   **Validate:** `./mvnw -T1C clean verify`.  
   **Satisfies:** AC01, AC26.
3. **CREATE** `basis-protocol/src/main/resources/sbe/basis.xml`.  
   **Validate:** schema validation, generated-code reproducibility, compatibility tests.  
   **Satisfies:** AC07, AC14, AC27, AC28.
4. **CREATE** exact parsers, numeric conversions, and instrument definitions in
   `basis-core`.  
   **Validate:** unit/property/fuzz tests and slow-oracle comparison.  
   **Satisfies:** AC02, AC03.
5. **CREATE** market-data contracts in `basis-venue-api`.  
   **Validate:** architecture tests show no venue dependency in core; ring
   overflow and lane-health tests cannot hide publication loss.  
   **Satisfies:** AC05, AC23.
6. **CREATE** Bybit public session/parser in `basis-venue-bybit`.  
   **Validate:** golden wire, fragmentation, fuzz, reconnect, and allocation tests.  
   **Satisfies:** AC04, AC05.
7. **CREATE** Deribit public session/parser in `basis-venue-deribit`.  
   **Validate:** same suite plus raw-entitlement behavior.  
   **Satisfies:** AC04, AC05.
8. **CREATE** primitive/reference books and trust trackers in `basis-core`.  
   **Validate:** differential/property/JMH/burst replay.  
   **Satisfies:** AC04, AC05, AC17.
9. **CREATE** strategy definition schema/API, certified payoff/carry/hedge
   models, and explicit registry.  
   **Validate:** representative and synthetic onboarding contract tests.  
   **Satisfies:** AC02, AC21.
10. **CREATE** executable pricing and reusable cross-venue basis model.  
    **Validate:** economic and temporal-coherence boundary/property tests plus
    BigDecimal oracle.  
    **Satisfies:** AC06, AC07, AC21, AC27.
11. **CREATE** risk/OEMS/execution group state machines in `basis-core`.  
    **Validate:** model tests, exhaustive small-state transitions, and urgent
    command/path-health progress under market-data/timer/order-agent bursts.  
    **Satisfies:** AC08-AC13, AC22, AC24, AC27, AC29.
12. **CREATE** fake venues/virtual clock/scenarios in `basis-sim`.  
    **Validate:** stable replay digest and failure matrix.  
    **Satisfies:** AC10-AC14.
13. **CREATE** journal/snapshot/replay/projector in `basis-journal`.  
    **Validate:** kill-point, corruption, backpressure, rebuild tests.  
    **Satisfies:** AC13, AC14, AC25.
14. **CREATE** Bybit private/order gateway.  
    **Validate:** scripted fake venue then testnet contract suite, including
    complete private-fill-to-transport-write trace correlation.  
    **Satisfies:** AC10-AC12, AC15, AC28.
15. **CREATE** Deribit private/order gateway.  
    **Validate:** scripted fake venue then testnet contract suite with the same
    stage contract and venue-specific semantics.  
    **Satisfies:** AC10-AC12, AC15, AC28.
16. **CREATE** application assembly/control/watchdogs/runbooks.  
    **Validate:** full lifecycle, scheduler fairness/starvation, and operator
    path-health/acceptance tests.  
    **Satisfies:** AC13, AC14, AC19, AC24, AC26, AC28, AC29.
17. **RUN** testnet certification and soak.  
    **Validate:** published certification report with zero unresolved critical findings.  
    **Satisfies:** AC15, AC17.
18. **RUN** production shadow/economic holdout.  
    **Validate:** signed economic-gate report.  
    **Satisfies:** AC16.
19. **RUN** production-host tuning/security/operational review.  
    **Validate:** stage/end-to-end performance gates, induced unsafe-path test,
    restore drill, security review, and runbook drill.  
    **Satisfies:** AC17-AC20, AC27-AC29.
20. **RUN** bounded mainnet canary.  
    **Validate:** daily decision/fill/reconciliation and realized-P&L review.  
    **Satisfies:** final acceptance.

---

# Testing strategy

## Unit and property tests

- Decimal parsing, checked arithmetic, ticks/lots/multipliers, linear/inverse
  payoff, hedge-ratio rounding, carry, conversion, and PnL.
- Strategy-definition schema, compatibility, model registry, capacity, and
  per-strategy isolation.
- SBE encode/decode and schema evolution.
- Book snapshot/update/image, ordering, deletion, duplicates, capacity, gaps.
- Pricing cost components and conservative rounding.
- Per-leg age and cross-leg skew boundaries, same-clock requirement, distinct
  `PRICE_NOT_TEMPORALLY_COHERENT`, and opportunity evidence fields.
- State transition tables, idempotency, reservations, fills, and imbalance.
- Rate limiter priority and emergency capacity.
- Hedge-path state hysteresis and every queue/agent/session/rate/latency input.

## Differential and model tests

- Primitive book versus TreeMap reference after every event.
- Fixed-point economics versus BigDecimal oracle at boundary values.
- OEMS implementation versus an executable state-machine model.
- Interleaved multi-strategy execution versus isolated reference instances.
- Opportunity output against a slow temporal-coherence oracle over asymmetric
  feed delays and threshold boundaries.
- Replay terminal digest across repeated runs and compatible releases.

## Integration and venue-contract tests

- Sanitized golden WebSocket and HTTP messages.
- Fragmented/coalesced frames and arbitrary JSON field ordering.
- Deterministic fake venues before any network test.
- Testnet suites are opt-in, credential-gated, and never part of hermetic verify.

## Fault and recovery tests

- Drop/duplicate/reorder/delay every inbound class.
- Disconnect each socket at every order transition.
- Lose command acknowledgement before/after venue acceptance.
- Fill during cancel and reconnect.
- Fill journal/archive/rings and exhaust rate limits.
- Keep both books individually trusted while varying their receive skew across
  the strategy threshold; only the combined opportunity may fail.
- Sustain a market-data burst while injecting private fills, kill commands,
  timer storms, and journal high water; assert bounded queue age and urgent
  progress rather than only eventual completion.
- Kill process after every state-changing event; replay and reconcile.
- Corrupt/truncate snapshots and ensure fallback to earlier snapshot/full replay.

## Performance tests

- JMH for numeric parsing, book mutation/query, SBE codecs, pricing, risk, and
  state transitions.
- End-to-end replay with real burst distributions and coordinated-omission-aware
  latency recording.
- Stage-correlated market-data-to-transport-write and private-fill-to-hedge-write
  histograms, including urgent queue age and deliberately stalled order agents.
- Allocation profiling/JFR, perf counters, CPU/cache/branch analysis on Linux,
  and long-run jitter/GC/thermal tests.
- Store hardware, OS, JVM flags, artifact hash, configuration hash, and input
  capture hash with every result.

# Validation commands

These become valid as the corresponding modules are created:

```bash
./mvnw -T1C clean verify
./mvnw -Pproperty-tests verify
./mvnw -Preplay-tests verify
./mvnw -Pchaos-tests verify
./mvnw -pl basis-benchmarks -am package
java -jar basis-benchmarks/target/benchmarks.jar
./mvnw -Pvenue-contract-tests -Dvenue=bybit verify
./mvnw -Pvenue-contract-tests -Dvenue=deribit verify
```

Networked profiles must refuse to run unless the requested environment is
explicit, credentials come from the approved secret provider, and mainnet
order transmission remains separately armed.

# Definition of done for v1

V1 is done only when the Phase 15 canary gate passes. A compiled application,
passing unit tests, successful testnet order, attractive backtest, or low median
latency is not individually sufficient. Completion requires correctness,
recovery, tail latency, security, operational readiness, and positive realized
net economics under the approved capital envelope.

# Amendments

- 2026-09-20 - Added v1 cross-leg temporal-coherence, full stage/end-to-end
  latency, and hedge-path-health gates. Added a separate post-v1 design contract
  for passive maker/aggressive hedge execution and evidence-driven regional cells.
