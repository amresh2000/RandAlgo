# Architecture - Bybit/Deribit Basis OMS

**Status:** Accepted for implementation  
**Decision date:** 2026-09-19  
**Last amended:** 2026-09-20  
**Initial deployment:** One host, one low-latency execution process  
**Evolution target:** Independently deployable venue-proximate execution cells

This document records system-level decisions. The normative construction
contract for every component is [Component Design](component-design.md), and the
delivery order and gates are in the [Implementation Plan](implementation-plan.md).
Deferred passive-maker and regional-cell constraints are in
[Advanced Execution Design](advanced-execution-design.md).

## 1. Problem and goals

Build a correct, observable, and predictably low-latency Java platform for
onboarding and executing cross-venue basis strategies between Bybit and
Deribit. A strategy may pair supported perpetual or dated futures across the
two venues and currencies, provided its payoff, hedge ratio, carry, conversion,
and execution policy are explicitly modeled and certified. The first production
version runs as one execution cell. It must be
safe under partial fills, stale or gapped books, disconnects, duplicate events,
timeouts, ambiguous order outcomes, process restarts, and venue-specific
quantity and order semantics.

The primary goal is not the smallest benchmark number. It is profitable
execution with bounded leg risk and enough evidence to explain every decision
and fill. Latency work is valuable only after market-data correctness, order
state, recovery, and risk invariants are demonstrated.

### Goals

- Correct local books from venue-native snapshot/update contracts.
- A repeatable onboarding path for new Bybit/Deribit basis strategies without
  changing venue sessions, books, risk, OEMS, or recovery code.
- Executable, fee/funding/slippage-aware basis calculations at a requested size.
- Cross-leg temporal-coherence checks so two individually fresh books cannot
  create a false opportunity merely because their local receive times are too
  far apart.
- A single-writer state machine for books, strategy, positions, risk, and orders.
- No steady-state allocation, blocking I/O, database access, or text logging on
  the decision path.
- Deterministic capture and replay of normalized events and decisions.
- End-to-end market-data-to-wire and fill-to-hedge-wire latency evidence, with
  latency degradation treated as a risk input rather than dashboard-only data.
- Explicit UNKNOWN order handling and venue reconciliation.
- Pre-positioned collateral; no asset transfer in the execution path.
- A controlled progression from offline replay to testnet, shadow, canary, and
  limited production trading.
- Boundaries that allow the execution cell to move close to a venue later.

### Non-goals for version one

- A generic institutional OMS supporting arbitrary asset classes and algos.
- Multi-region active-active order ownership.
- A GUI, public client API, FIX client gateway, or portfolio accounting suite.
- Venues other than Bybit and Deribit, arbitrary asset classes, or a generic
  strategy marketplace.
- Runtime loading of untrusted strategy bytecode. New logic is compiled and
  reviewed; strategies using existing models are onboarded by definition.
- Spot and options until separate product/payoff support is deliberately added.
- Kafka, NATS, Kubernetes, microservices, or a service mesh in the hot path.
- Native Bybit MMWS/SBE or Deribit Starbase access before entitlement and an
  economic case exist.
- Automatic treasury transfers or collateral rebalancing.
- Claiming "HFT" performance from tests on a developer laptop.

## 2. Inputs and precedence

This document supersedes the implementation choices in the earlier PDF,
*Designing a Multi-Venue, Multi-Region OMS for Low-Latency Algorithmic Trading*,
while preserving its most important architectural principle: global authority
and durable controls must eventually be separated from venue-proximate
execution cells.

Precedence when sources disagree:

1. Observed, recorded venue behavior for the exact account and product.
2. Current production venue documentation and schemas.
3. This architecture and its ADRs.
4. Tests and implementation comments.
5. The earlier PDF and the previously created Bitbucket prototypes.

The previous `messaging`, `market-data-feeder`, and `oems` repositories are
reference material only. They are not dependencies and code is not copied
without a deliberate review.

## 3. Approaches considered

### A. Single-process monolith without durable boundaries

Fast to start and capable of excellent latency, but business logic, venue I/O,
persistence, and operations tend to become inseparable. Recovery and future
regional deployment become costly.

### B. Cell-first modular architecture - selected

One process owns the latency-sensitive path. Maven modules and explicit binary
contracts preserve boundaries. Network I/O agents communicate with one core
owner through bounded SPSC channels. The core invokes book, pricing, risk,
strategy, and order state directly on the same thread. Audit and control are
asynchronous.

This provides a short path now and a credible route to regional cells later.

### C. Distributed feeder, strategy, risk, and OEMS from day one

This resembles the mature PDF topology but adds serialization, backpressure,
deployment, state ownership, and recovery problems before the strategy has
proved an economic edge. It is rejected for version one.

## 4. Accepted decisions

| ID | Decision |
|---|---|
| A01 | Use Java 25 LTS and Maven 3.9.16 via wrappers/toolchains. |
| A02 | Package the hot path as one process with one state-owning core thread. |
| A03 | Use Netty 4.2.x for TLS/WebSocket transport and custom allocation-controlled venue codecs. |
| A04 | Use primitive scaled `long` values in the hot path; prohibit `BigDecimal`, streams, boxed collections, and wall-clock calls there. |
| A05 | Use fixed-capacity primitive array books for the approved bounded-depth feeds. |
| A06 | Use one Agrona SPSC binary ring per producer/consumer lane; use direct calls inside the core. |
| A07 | Use SBE as the versioned process/journal contract and Aeron IPC only across real process boundaries. |
| A08 | Do not deploy NATS or Kafka initially. Control traffic is too small to justify a broker. |
| A09 | Use WebSocket order entry and private execution/order streams; reserve REST/HTTP for bootstrap and reconciliation. |
| A10 | Treat an order timeout or ambiguous acknowledgement as exposure in `UNKNOWN`, never as a rejection. |
| A11 | Use deterministic client order IDs and single-writer venue-session ownership. |
| A12 | Record asynchronously to Aeron Archive, snapshot core state, then reconcile both venues before re-arming after restart. |
| A13 | Fix the venue universe to Bybit and Deribit; select and certify strategy pairs through an onboarding contract rather than hard-coding instruments in the platform. |
| A14 | Provide aggressive initiation plus immediate aggressive hedge as the first reusable execution policy; add passive modes only after measured live evidence. |
| A15 | The system fails closed: invalid market data or unresolved execution state prevents new exposure while preserving cancel/hedge capacity. |
| A16 | Keep product economics, signal logic, and execution policy separate so a supported new strategy is configuration plus evidence, not a fork of the OMS. |
| A17 | Keep core state in preallocated heap primitive arrays; use direct/off-heap buffers only at binary transport, ring, and journal boundaries. |
| A18 | Use separate urgent and normal SPSC order-command lanes per venue; hedge, cancel, and unwind traffic always drains first and owns reserved capacity. |
| A19 | Use a bounded priority/fair core duty cycle: health and kill state are sampled first, private executions immediately drive urgent risk-reducing actions, market data is round-robin, and affected strategies evaluate after a trusted book mutation. |
| A20 | Deploy one execution-cell JVM initially; operator handling may be cold threads in that JVM and moves over the same SBE/Aeron contract only when a real process boundary is justified. |
| A21 | Require both per-leg freshness and a strategy/feed-profile-specific maximum cross-leg skew measured from the same process-local monotonic clock. Excess skew rejects the combined opportunity without falsely corrupting either valid book. |
| A22 | Timestamp the complete receive/decode/core/order/write chain. Market-data-to-wire and private-fill-to-hedge-wire tails are release gates; hedge-path degradation blocks new exposure. |
| A23 | Keep passive maker/aggressive hedge outside v1. It is a separate execution policy with post-only semantics, quote invalidation, cancel-race handling, full resting-quantity reservation, queue/adverse-selection evidence, and certification. |
| A24 | Keep single-cell placement and any dual-cell promotion evidence-driven. A future maker-side authority sends idempotent canonical hedge requirements to a venue-local hedge cell; the hedge cell chooses native quantity/price from its freshest local state. |

Dependency versions are pinned in the root dependency management. Upgrades
require protocol replay, performance comparison, and an ADR when behavior or
major versions change. Research-time current versions were Aeron 1.53.2,
Agrona 2.6.1, SBE 1.40.2, Netty 4.2.18.Final, JMH 1.37, and JUnit 5.14.4.
The JUnit 5 line is selected because jqwik 1.10.1 targets JUnit Platform
1.14.4; a move to JUnit 6 requires an explicit compatibility check.

## 5. Product, strategy, and economic model

### Venue and product universe

The platform boundary is Bybit plus Deribit. Instruments are runtime metadata,
not Java constants. Initially supported product families are perpetual and
dated futures whose economics can be represented by a certified payoff model:

- linear contracts;
- inverse/reversed contracts;
- perpetual funding carry;
- dated-future expiry and settlement carry;
- explicit quote, settlement, collateral, multiplier, tick, and lot units.

Instrument metadata is loaded from both venues at startup, converted to an
immutable `InstrumentDefinition`, and compared with the strategy's allow-listed
contract. Trading for that strategy stays disabled if tick size, amount step,
contract multiplier, settlement, expiry, lifecycle, or fee configuration
differs from the certified definition.

`BTCUSD` inverse perpetual versus `BTC-PERPETUAL` remains a useful first
reference candidate because its payoff and collateral are closely aligned. It
is not an architectural decision, and Phase 0 may select a different first
strategy if measured economics or connectivity are better.

### Strategy onboarding contract

A `BasisStrategyDefinition` contains:

```text
strategyId and version
Bybit leg selector and Deribit leg selector
canonical underlying and risk currency
payoff model per leg
hedge-ratio model and permitted rounding
price/currency conversion sources and freshness
signal and entry/exit thresholds
carry model: funding, expiry, borrow, or configured combination
liquidity haircut and latency-risk model
execution-policy ID and parameters
risk envelope, maximum imbalance, and capital limits
market-data depth/frequency/freshness requirements
per-leg maximum age and cross-leg receive-time skew
market-data-to-wire and fill-to-hedge-wire latency budgets
session/account bindings
effective time and configuration hash
```

Onboarding tiers are explicit:

1. **Definition-only:** Both legs use certified product/payoff, carry, signal,
   and execution models. Add configuration, fixtures, economic evidence, and
   certification; no production Java changes.
2. **New model:** A new payoff, carry, signal, or execution model requires a
   compiled module, property/oracle tests, benchmarks, and an ADR. Venue/OEMS
   code still must not fork.
3. **New venue or asset class:** Outside this architecture and requires a new
   architecture decision.

Strategies are registered explicitly at process assembly. There is no runtime
classpath scanning or arbitrary plugin loading. Capacities for instruments,
books, strategies, execution groups, and child orders are fixed at startup.

Every strategy definition follows the same lifecycle:

```text
DRAFT -> CONTRACT_VALIDATED -> REPLAY_CERTIFIED -> SHADOW
      -> TESTNET_CERTIFIED -> CANARY -> ACTIVE

Any state -> SUSPENDED -> RETIRED
```

Promotion is per strategy version. Editing a material leg, model, threshold,
account, or risk parameter creates a new version and invalidates downstream
certifications. Shared venue adapters and the OMS are certified independently;
strategy onboarding proves compatibility with them rather than cloning them.

### Normalization and executable edge

There is no universal assumption that both legs use USD notional or identical
payoffs. Each certified `PayoffModel` maps native price/quantity to:

- canonical underlying delta;
- risk-currency notional and cash flows;
- collateral and margin consumption;
- fee, funding, settlement, and realized PnL units.

The `HedgeRatioModel` chooses native quantities that match the strategy's
canonical exposure within declared conservative rounding. Currency conversion
uses an explicit, timestamped, freshness-gated reference source.

For same-quote, like-payoff legs, the familiar form is:

```text
grossEdgeBps(q) = 10_000 * (sellVwap(q) - buyVwap(q)) / referencePrice
```

For every strategy, the general decision is:

```text
netEdge = normalized executable proceeds
        - normalized executable cost
        - fees
        - expected carry/funding through the holding horizon
        - expected slippage
        - latency risk
        - conversion cost/risk
        - safety reserve
```

The strategy never trades a midpoint difference. VWAPs come from executable
opposite-side depth after an empirical liquidity haircut. Fees and carry are
account/product-specific, versioned, and freshness-gated.

The first reusable execution policy sends an IOC/marketable limit to the
selected initiating venue. Actual fills, not acknowledgements, generate hedge
orders. Initiating/hedging roles are chosen per strategy and may change only
through its validated policy and current measured economics.

## 6. Runtime topology

```text
                      control/config agent
               (cold threads initially; process later)
                              |
               bounded lane; SBE/Aeron if split
                              v
+-------------------------------------------------------------------+
|                    execution-cell process                         |
|                                                                   |
| Bybit public WS -> MD I/O + decoder -> SPSC --+                   |
| Deribit public WS -> MD I/O + decoder -> SPSC +--> CORE OWNER     |
| Bybit private WS -> execution decoder -> SPSC -+    THREAD         |
| Deribit private WS -> execution decoder -> SPSC+      |            |
|                                                        |            |
|      books -> executable pricing -> strategy -> risk -> OEMS       |
|                                                        |            |
|                         +------------------------------+            |
|                         |                                           |
|                  SPSC order commands                               |
|                    /                 \                              |
|        Bybit order I/O             Deribit order I/O                |
|                                                                   |
| core events -> nonblocking SBE publication -> archive/telemetry    |
+-------------------------------------------------------------------+
```

### Thread ownership

- Each Netty event loop owns its socket, TLS state, input buffer, and venue
  decoder.
- Market-data and order/private traffic use separate sockets and event loops.
- A dedicated core thread is the only writer of books, strategy state,
  reservations, positions, execution groups, and child orders.
- Each venue order I/O agent owns its authenticated order-entry connection.
- Archive, metrics, configuration, and operator endpoints never execute on the
  core thread.
- No shared mutable domain object crosses a thread boundary. Boundary messages
  contain primitive values in bounded binary buffers.

Thread affinity is configuration, not hard-coded. Production Linux pins the
core and order I/O agents to isolated physical cores after measurement. Network
IRQ/RSS, NUMA placement, CPU frequency policy, power management, and time sync
are part of the deployment profile.

The core duty cycle and each binary lane are specified in
[Component Design section 8](component-design.md#8-in-process-messaging-and-core-duty-cycle).
Private fills and urgent risk-reducing commands cannot queue behind normal
initiation or an unbounded market-data drain.

## 7. Market-data design

### Channels

- Bybit: the adapter selects the public `linear` or `inverse` WebSocket from the
  instrument definition and subscribes to `orderbook.{depth}.{symbol}`. Depth
  50 currently provides a 20 ms bounded snapshot/delta feed. Preserve `u`,
  `seq`, `ts`, and matching-engine `cts` independently.
- Deribit: the adapter subscribes to
  `book.{instrument}.none.{depth}.{interval}`. The preferred bounded profile is
  depth 20 with `raw` when the account is entitled; it is a no-grouping image
  feed with nominal 1 ms aggregation.
- If Deribit raw entitlement is unavailable, use the same depth with `100ms`
  only for strategies whose certified latency/economic model permits it. A
  slower feed is never silently treated as equivalent.

Each configured instrument owns a separate fixed-capacity book and trust state.
Subscriptions are the union of active strategy requirements and are resolved at
startup. Adding a definition-only strategy may add subscriptions and books but
does not change adapter code.

The Deribit bounded channel is treated as a full top-N image on every
notification unless captured wire evidence and official contract tests prove a
different semantic. The full-depth incremental channel requires a different
book implementation and is out of v1 scope.

### Parser rules

- Parse directly from UTF-8 bytes/Netty buffers into fixed-point primitives.
- Do not construct a JSON tree, `String`, list, map, record, or `BigDecimal` per
  message/level.
- Reject excessive nesting, oversized frames, non-ASCII numeric tokens,
  exponent notation where unsupported, scale overflow, negative sizes, and
  out-of-range level counts.
- Preserve unknown fields by skipping them; required missing/duplicate fields
  invalidate the event.
- Fuzz parsers and replay the venue corpus on every build.

### Book structure

Each side has parallel arrays sorted best-first:

```text
long[] prices
long[] quantities
int size
```

Price and quantity scales belong to the immutable instrument definition. Array
capacity equals the feed contract plus a small explicit safety bound; it never
grows at runtime. Updates use search plus bounded `System.arraycopy`. Reads are
primitive methods such as best price, quantity through price, and executable
VWAP. No API returns collections from the hot book.

A slow `TreeMap` reference book exists only in tests. Recorded and generated
event streams are applied to both implementations and their observable states
must match.

### Trust state

```text
DISCONNECTED -> SYNCING -> TRUSTED
     ^             |          |
     +-------------+----------+
          gap, malformed data,
          stale deadline, epoch reset
```

Sequence rules are venue-specific. Bybit `u` is not assumed to increment by
exactly one unless a documented and observed contract establishes that rule.
Deribit full-depth incremental continuity uses `prev_change_id == change_id`;
the bounded image channel is validated according to its actual wire contract.
Any gap, impossible crossed book, stale deadline, or reconnect revokes trading
permission for every strategy depending on that book.

## 8. Core domain and state

### Principal entities

- `InstrumentDefinition`: venue IDs, product/payoff, price scale, native amount
  scale, contract multiplier, settlement, tick/lot/min/max, lifecycle.
- `BasisStrategyDefinition`: versioned leg selectors, canonical risk unit,
  payoff/hedge/carry/signal/execution model IDs, data requirements, limits,
  accounts, effective time, and configuration hash.
- `PayoffModel`: exact conversion between native quantity/price and canonical
  delta, notional, cash flow, margin, fee, funding, settlement, and PnL units.
- `StrategyInstance`: bounded runtime state for one validated definition,
  independent from reusable model implementations.
- `BookState`: bounded arrays, venue sequence metadata, epoch, trust and
  freshness timestamps.
- `BasisOpportunity`: direction, canonical exposure/risk unit, native leg
  quantities, executable prices, complete cost breakdown, evidence sequences,
  per-leg receive times/ages, cross-leg skew, input generations, and expiry.
- `ExecutionGroup`: the two legs, initiating/hedging roles, target, fills,
  reservations, maximum imbalance, deadline, and terminal reason.
- `ChildOrder`: deterministic identity, venue session ownership, native request,
  potential exposure, state, cumulative fill, and venue IDs.
- `RiskEnvelope`: approved gross, net, unhedged, per-venue, collateral, rate,
  and loss limits with an expiry/generation.
- `PositionState`: normalized and venue-native positions, balances, reserved
  collateral, realized fees/funding/PnL, and reconciliation status.
- `ExecutionEvent`: immutable versioned fact used for journal and replay.

### State machines

Child orders include at minimum:

```text
CREATED -> SEND_PENDING -> SENT -> ACKNOWLEDGED -> WORKING
                                  |                 |
                                  v                 v
                               UNKNOWN       PARTIALLY_FILLED
                                  |            /          \
                                  v           v            v
                             RECONCILING   FILLED     CANCEL_PENDING
                                                        /      \
                                                   CANCELLED  UNKNOWN

Any nonterminal state may become REJECTED only from authoritative evidence.
```

Execution groups include `PLANNED`, `RESERVED`, `INITIATING`, `HEDGING`,
`BALANCED`, `UNWINDING`, `UNKNOWN`, `FAILED`, and `COMPLETE`. A terminal group
requires both child-order resolution and zero unacceptable residual imbalance.

### Hard invariants

```text
confirmed fills + maximum possible outstanding fills <= authorized exposure
abs(unhedged canonical exposure) <= execution-group max imbalance
new exposure requires both books TRUSTED and fresh
new exposure requires cross-leg receive-time skew within the certified profile
new initiation requires valid hedge capacity, collateral, and rate capacity
new initiation requires a HEALTHY hedge path and, when distributed, hedge route
UNKNOWN orders reserve their maximum possible remaining exposure
one child order has exactly one active owner session and generation
risk-reducing cancel/hedge traffic has capacity ahead of optional initiation
an acknowledgement is not a fill and is not terminal execution evidence
no restart permits trading before journal replay plus venue reconciliation
```

## 9. Risk and execution policy

Risk checks occur in a fixed order and return numeric reason codes:

1. Global, venue, strategy, symbol, account, and execution-group kill state.
2. Session health and order ownership generation.
3. Book trust, per-leg freshness, cross-leg skew, sequence evidence, and
   opportunity expiry.
4. Tick/lot/notional validity and exact native conversion.
5. Parent/execution-group limit and reference-price band.
6. Gross, net, unhedged, position, collateral, and daily loss limits.
7. Hedge liquidity and maximum imbalance.
8. Duplicate/runaway detection.
9. Venue/session rate-limit capacity with emergency capacity reserved.
10. Hedge-path health from urgent queue age, order-agent progress, socket state,
    rate capacity, and recent fill-to-wire tails.

The first reusable execution policy is deliberately narrow:

- A configurable, preallocated maximum number of concurrent groups per strategy;
  the first live canary sets this maximum to one.
- Marketable limit/IOC initiation with a strict worst price.
- Immediate hedge from actual incremental fills.
- Bounded hedge pay-up ladder and emergency unwind.
- Stop further initiation while any imbalance or UNKNOWN state exists.
- A configurable cooldown after a feed/session/reconciliation incident.

Passive initiation, parallel IOC, TWAP, and inventory skewing require separate
evidence and ADRs. A new strategy chooses only from certified execution
policies; strategy configuration cannot bypass the safety layer.

## 10. Messaging and backpressure

### In-process

Use one Agrona `OneToOneRingBuffer` with direct/off-heap backing per actual SPSC
lane. Each lane has one producer and one consumer, fixed capacity, message type
IDs, and flyweight codecs. Each venue order agent has separate urgent and normal
command lanes. Strategy, pricing, risk, and order state call one another
directly because they share the core owner thread.

### Process boundaries

Use SBE messages over Aeron IPC for control commands, journal events, replay,
and telemetry requiring lossless delivery. `tryClaim`/`offer` is nonblocking;
the caller handles the result. No generic object envelope or serialized Java
object enters the hot path.

### Backpressure policy

| Lane | Full/unavailable behavior |
|---|---|
| Market data -> core | Mark affected book invalid, increment epoch, reconnect/resubscribe; never silently continue. |
| Private executions -> core | Stop initiation immediately; prioritize draining; disconnect/reconcile if integrity cannot be preserved. |
| Core -> order I/O | Stop initiation and enter fault state; cancels/hedges use reserved lane/capacity. |
| Core -> durable journal | Stop new initiation; continue risk-reducing actions; alert. |
| Metrics/debug telemetry | Drop with counters; never delay trading. |
| Control -> core | Reject with explicit busy/stale-generation result. |

No hot-path retry loop parks or sleeps. Retrying is an agent state advanced on
subsequent duty cycles with a deadline and bounded attempt policy.

## 11. Persistence, replay, and recovery

- Publish normalized inputs, decisions, risk results, outbound commands,
  acknowledgements, executions, state transitions, and operator actions as SBE
  events.
- Record the event stream with Aeron Archive to dedicated local storage.
- Create periodic versioned core snapshots containing positions, reservations,
  sequence epochs, open/unknown orders, and risk generation.
- Project events asynchronously to PostgreSQL for query, reporting, and TCA.
  PostgreSQL is not the hot-path source of truth.
- Record raw venue frames in bounded rotated capture files during development,
  certification, and selected production windows, with credentials redacted.

Restart sequence:

1. Start in `DISARMED` with outbound initiation disabled.
2. Load the latest compatible snapshot and replay subsequent journal events.
3. Establish private streams and query open orders, recent fills, positions,
   balances, and venue session state.
4. Resolve every open or UNKNOWN child by deterministic client ID and venue ID.
5. Compare reconstructed positions/reservations with both venues.
6. Resynchronize both market books and satisfy the freshness warm-up.
7. Require an explicit or policy-approved arm transition.

If the journal may have lagged before a crash, venue state wins for executions;
differences produce compensating reconciliation events, never history edits.

## 12. Clocks and observability

- Use `System.nanoTime()` for local elapsed time and deadlines.
- Preserve venue timestamps and local epoch receive timestamps separately.
- Never subtract monotonic timestamps from different processes/hosts.
- Production hosts run monitored NTP/Chrony initially; PTP and hardware receive
  timestamps are an evidence-driven later enhancement.
- Timestamp receive, decode, book apply, decision, risk complete, enqueue,
  socket write, acknowledgement, fill, hedge enqueue/write/fill.
- Define two mandatory application paths: market-data receive to order socket
  write and private-fill receive to hedge socket write. Kernel/NIC timestamps
  are recorded when available but do not replace application-stage timestamps.
- Use HdrHistogram-style coordinated-omission-aware histograms for p50, p90,
  p99, p99.9, p99.99, and max.
- Emit counters and binary reason codes from the core. Render text and export
  metrics asynchronously.

Initial engineering budgets, measured on the target Linux host after warm-up:

- Steady-state application allocations on the core thread: zero.
- Normalized event dequeue through book, decision, risk, and outbound enqueue:
  p99 <= 50 microseconds and p99.9 <= 100 microseconds.
- Decision through outbound enqueue alone: p99 <= 10 microseconds.
- Market-data-to-wire and private-fill-to-hedge-wire budgets are set from Phase
  0/14 target-host measurements per venue/feed profile. Both receive explicit
  p99 and p99.9 release thresholds before live trading.
- No silent application-queue loss under the certified load. A market-data
  publication failure invalidates the book; an unrecorded crash tail is resolved
  from venue truth during mandatory reconciliation.
- Feed-to-decision and fill-to-hedge-wire distributions must be reported, not
  hidden behind averages.

These are engineering gates, not claims about Internet or venue latency.

## 13. Security and operational controls

- Separate least-privilege market-data and trading credentials where supported.
- Disable withdrawals on trading keys and IP-allow-list production keys.
- Keep secrets in the deployment secret manager; never in config files, command
  lines, heap dumps, raw captures, metrics, or logs.
- Use dedicated venue subaccounts and explicit account IDs in every order and
  event.
- Authenticate and authorize operator commands; every arm, kill, config change,
  and manual reconciliation action is journaled.
- Kill scopes: global, strategy, venue, account, instrument, execution group,
  and session.
- A kill prevents new exposure and attempts cancellation. It does not suppress
  reconciliation or a specifically authorized risk-reducing hedge/unwind.
- Configure venue cancel-on-disconnect where its precise session semantics have
  been tested; never assume it replaces local recovery.

## 14. Build, testing, and release policy

- Reproducible Maven wrapper build with dependency convergence, checksums,
  locked plugin versions, compiler warnings as errors, formatting, static
  analysis, and forbidden-API rules for hot packages.
- Unit tests for arithmetic, codecs, state transitions, and every risk reason.
- Property tests for book ordering, quantity conservation, exposure bounds,
  idempotency, and replay determinism.
- Differential tests against slow reference books and a slow BigDecimal
  economic model.
- Golden-wire tests from sanitized venue captures.
- Model-based OEMS tests with duplicate, reordered, delayed, missing, and
  conflicting events.
- Integration tests against deterministic fake venues before testnet.
- Fault tests for disconnects, partial writes, backpressure, malformed frames,
  clock jumps, archive failure, process kill, and restart reconciliation.
- JMH microbenchmarks plus end-to-end replay/load/soak tests. Benchmarks are
  compared against checked baselines on dedicated runners, not asserted in
  ordinary unit tests.
- No live deployment from a developer workstation. Releases are immutable,
  signed, configuration-hashed, and promoted through replay, testnet, shadow,
  canary, and bounded-capital stages.

## 15. Evolution to the PDF target

The initial execution cell already contains the boundary required for regional
deployment. Evolution occurs only after single-cell live evidence:

1. Measure candidate regions against both venues. Current official documentation
   places Bybit in AWS Singapore and Deribit primary infrastructure in London/
   LD4, but deployment treats locations as revalidated inputs, not constants.
2. Move the same immutable cell artifact to the measured best single-cell region.
3. Split control, archive, and reporting into separately deployed processes.
4. Deploy one fenced execution cell per venue/region with pre-authorized risk
   envelopes and pre-positioned inventory.
5. Assign each execution group to one authority, normally the maker-side cell
   where the uncertain fill occurs. Send canonical delta requirements, not stale
   remote native order instructions, to the hedge-side cell.
6. Add direct SBE/Aeron UDP cell-to-cell hedge messages with acknowledgements,
   deduplication, deadlines, ownership generations, replay, and reconciliation.
7. Add a logically central parent OMS/global risk allocator, never a synchronous
   database/RPC dependency of a hedge.
8. Pursue Bybit MMWS/SBE or Deribit Starbase only when entitlement, deployment
   location, and measured P&L justify the integration.

Dual cells do not remove Singapore-London propagation. They localize each venue
session and place the unavoidable wide-area hop on a controlled inter-cell
channel. Promotion requires measured proof that reduced venue-connection tails
and better local decisions exceed infrastructure cost and distributed-state risk.

## 16. Missing pieces and mandatory spikes

### Spike S1 - Strategy onboarding and economic viability

**Question:** Can a candidate Bybit/Deribit pair be expressed through the
onboarding contract, and is it economically tradable after real fees, carry,
depth, slippage, latency, conversion, collateral, and expiry risks?  
**Experiment:** For each candidate, validate both instrument/payoff definitions,
capture synchronized production feeds for at least seven representative days,
replay executable sizes, and calculate opportunity duration and conservative
net edge. Onboard at least two synthetic definitions in tests to prove that the
kernel is not coupled to the reference pair.  
**Decision rule:** Certify a strategy only if it uses supported models, passes
all invariant/replay tests, and its predeclared policy remains positive after
conservative costs with sufficient opportunity duration.

### Spike S2 - Venue wire contracts

**Question:** What are the exact snapshot/update, sequence, duplicate, restart,
and amount semantics for each channel profile admitted by a strategy?  
**Experiment:** Capture testnet and production public frames through reconnects
and controlled network loss; compare against official documentation and REST
snapshots.  
**Decision rule:** Codify only observed invariants. If bounded Deribit raw
images are unavailable or insufficient, select the 100 ms image for a slower
strategy or adopt a separately benchmarked full-depth structure.

### Spike S3 - Array book

**Question:** Does the fixed array book meet correctness and latency targets?  
**Experiment:** Differential/property testing plus JMH and burst replay against
the slow reference book.  
**Decision rule:** Keep arrays if correct and within the Phase 4 budget. Consider
a primitive radix/ordered structure only for an approved unbounded feed and a
measured array failure.

### Spike S4 - Network/thread/GC profile

**Question:** Which Netty native transport, ring sizing, idle strategy, and GC
profile gives the best tail behavior on the production host?  
**Experiment:** Replay the same burst trace under epoll versus io_uring where
supported, G1 versus ZGC, and bounded idle strategies while recording CPU,
allocation, and latency distributions.  
**Decision rule:** Select the simplest profile satisfying tail and operational
stability targets. Busy-spin is allowed only on reserved cores.

### Spike S5 - Testnet order semantics

**Question:** Can every create/amend/cancel/fill/timeout/reconnect state be
resolved without blind retransmission?  
**Experiment:** Fault-injected testnet certification with deterministic IDs,
private streams, queries, partial fills, duplicated messages, disconnects, and
process kills.  
**Decision rule:** No mainnet credentials until every unknown state converges or
causes a documented safe halt.

### Spike S6 - Temporal coherence and end-to-end latency

**Question:** Which per-leg age, cross-leg receive-time skew, and end-to-end
latency thresholds distinguish safe executable evidence from stale/asymmetric
views without rejecting nearly every opportunity for the selected feed modes?  
**Experiment:** Capture same-process monotonic receive times and every application
stage from network read through socket write under normal, burst, stalled order
agent, and private-fill workloads. Replay candidate age/skew thresholds against
net edge, false-opportunity rate, queue age, and fill-to-hedge slippage.  
**Decision rule:** A strategy/feed profile is certifiable only with predeclared
p99/p99.9 end-to-end gates and age/skew limits that remain economically positive
on holdout data. Breaching hedge-path limits blocks new exposure.

## 17. Open questions deliberately deferred

- Exact initial capital and per-trade risk limits: set immediately before the
  canary from account size and observed liquidity.
- Hosting region/provider: decide from measured RTT/jitter to both venues, not
  an assumed exchange location.
- G1 versus ZGC and epoll versus io_uring: resolve in S4 on target hardware.
- Aeron Archive replication/failover: single local archive for v1; design the
  regional durability topology before multi-host trading.
- First production strategy definition: select in Phase 0 from measured
  economics; `BTCUSD` versus `BTC-PERPETUAL` is a reference candidate, not a
  preselected outcome.
- Initial set of certified payoff/carry models: choose from the Phase 0 Bybit/
  Deribit capability matrix; unsupported families remain fail-closed.
- Exact per-strategy leg-age/skew and end-to-end latency limits: establish in S6
  from the selected feed profile and target host, not an arbitrary universal value.
- Dual-cell promotion: post-v1 only and conditional on a measured economic case,
  a certified idempotent inter-cell protocol, and distributed recovery drills.

## 18. Primary references

- [Bybit bounded order-book stream](https://bybit-exchange.github.io/docs/v5/websocket/public/orderbook)
- [Bybit server-location FAQ](https://bybit-exchange.github.io/docs/faq)
- [Bybit order and post-only semantics](https://bybit-exchange.github.io/docs/v5/order/create-order)
- [Bybit WebSocket order entry](https://bybit-exchange.github.io/docs/v5/websocket/trade/guideline)
- [Bybit fast execution stream](https://bybit-exchange.github.io/docs/v5/websocket/private/fast-execution)
- [Deribit bounded-depth order book](https://docs.deribit.com/subscriptions/orderbook/bookinstrument_namegroupdepthinterval)
- [Deribit full-depth sequencing](https://docs.deribit.com/subscriptions/orderbook/bookinstrument_nameinterval)
- [Deribit market-data best practices](https://docs.deribit.com/articles/market-data-collection-best-practices)
- [Deribit order-management best practices](https://docs.deribit.com/articles/order-management-best-practices)
- [Aeron releases](https://github.com/aeron-io/aeron/releases)
- [SBE tool guide](https://github.com/real-logic/simple-binary-encoding/wiki/Sbe-Tool-Guide)
- [Netty 4.2 releases](https://netty.io/news/)
- [Maven release history](https://maven.apache.org/docs/history)
- [JUnit framework releases](https://github.com/junit-team/junit-framework/releases)
