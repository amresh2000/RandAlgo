# Component Design - Bybit/Deribit Basis OMS

**Status:** Normative design for implementation  
**Last reviewed:** 2026-09-20  
**Parent:** [Architecture](architecture.md)  
**Execution order:** [Implementation plan](implementation-plan.md)

Passive maker and venue-proximate cell extensions are specified separately in
[Advanced Execution Design](advanced-execution-design.md); they are not v1 scope.

This document turns the accepted architecture into build contracts for each
component. It is deliberately more prescriptive than the architecture. The
words **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative. If measured
venue behavior or an implementation spike disproves a decision here, create an
ADR, update the affected contract tests, and then amend this document. Do not
silently diverge in code.

## 1. System-wide construction rules

### 1.1 Latency philosophy

Optimize in this order:

1. Correct units, book state, order state, and exposure.
2. Bounded work and fail-closed overload behavior.
3. Deterministic replay and operational recovery.
4. Tail latency and allocation.
5. Average latency and throughput.

A faster path that can trade on stale state, lose a fill, or create an
unbounded backlog is rejected. Median-only benchmark improvements are not a
reason to add complexity.

### 1.2 Hot, warm, and cold paths

| Path | Included work | Rules |
|---|---|---|
| Hot | decoded event enqueue, core dequeue, book mutation, pricing, strategy, risk, OEMS, order enqueue, private fill to hedge enqueue | bounded, nonblocking, no steady-state allocation, no text, no exceptions for expected outcomes |
| Warm | socket connect/authenticate, subscribe, reconnect, metadata refresh, reconciliation, snapshot encode, configuration activation | allocation permitted but bounded; never executes on the core thread |
| Cold | research, YAML/JSON parsing, reporting, PostgreSQL projection, dashboards, deployment tooling | clarity and safety take priority over nanoseconds |

The same class MUST NOT casually mix path classifications. Every production
package receives a `package-info.java` declaring its classification and owner
thread. Architecture tests enforce forbidden dependencies and APIs for hot
packages.

### 1.3 Memory and data representation

- Core state MUST use preallocated heap-resident primitive arrays. Normal Java
  arrays are preferred over off-heap state because they provide bounds checks,
  simple ownership, good locality, and no manual lifetime risk.
- Direct/off-heap buffers are used only at transport, SPSC, SBE, and archive
  boundaries where a binary buffer is the native contract.
- General object pools are forbidden on the hot path. Use fixed slot arrays with
  a generation counter, explicit free lists, and primitive fields instead.
- `String`, `BigDecimal`, collections, streams, capturing lambdas, futures,
  locks, reflection, and logging arguments are forbidden in steady-state core
  execution.
- Cold configuration converts external decimal text and identifiers once into
  scaled integers and dense integer IDs. Hot code refers to `instrumentId`,
  `strategySlot`, `accountId`, and `modelId`, never venue symbol strings.
- Numeric scale is part of the type contract. A raw `long` without a named unit
  is not accepted at a public component boundary.
- Checked arithmetic is mandatory for multiplication, scale conversion, and
  aggregation. Saturation is forbidden for monetary/exposure values because it
  can conceal risk.

### 1.4 Ownership and mutation

- The core thread is the sole writer of books, strategy runtime state, risk
  reservations, positions, execution groups, and child orders.
- Netty event loops own their channels, TLS engines, cumulation buffers, and
  venue parsers.
- Each order I/O agent owns exactly one authenticated order-entry session
  generation and its request-correlation table.
- The journal agent owns archive publication and snapshot file I/O.
- No mutable domain object crosses a thread. Threads exchange bounded binary
  messages or cache-line-isolated atomic health words.
- Read-only configuration is constructed fully, validated, and then published
  as an immutable generation. Configuration is never mutated in place.

### 1.5 Error semantics

Expected outcomes return primitive result/status codes and MUST NOT throw.
Examples include stale book, limit exceeded, no ring capacity, rate limit, and
unknown venue response. Exceptions mean a violated programming or runtime
invariant; the owning agent catches at its top-level boundary, emits a fault if
possible, and enters a non-trading state.

Every rejection and state transition has a stable numeric reason code in the
protocol schema. Human-readable text is rendered asynchronously.

## 2. Component map and dependency direction

```text
configuration/catalog -----> application assembly <----- operator control
          |                          |
          v                          v
strategy/model registry ---> execution core <----- ingress SPSC lanes
                                  |  ^                 ^
                 urgent/normal    |  |                 |
                 order commands   |  | normalized events
                                  v  |
                            venue order agents
                                  |
                                  v
                              exchanges

execution core ---> journal publication ---> Aeron Archive ---> projectors
       |                    |
       +--------------------+---> snapshots/replay/recovery
```

Compile-time dependency direction is:

```text
basis-protocol       <- venue adapters, journal, app
basis-core           <- strategy API/implementations, app, simulator
basis-strategy-api   <- strategy implementations, app
basis-venue-api      <- venue implementations, app, simulator
all concrete modules <- basis-app only for assembly
```

`basis-core` MUST NOT depend on Netty, Aeron, JSON/YAML, JDBC, a concrete venue,
or a concrete strategy. Venue implementations MUST NOT invoke strategy or risk
logic. Strategy implementations MUST NOT send orders directly.

## 3. Configuration, catalog, and strategy onboarding

### Responsibility

Turn signed cold-path definitions plus live venue metadata into an immutable,
dense runtime catalog. This component decides whether a strategy is eligible to
start, not whether a current opportunity may trade.

### Construction

1. Parse deployment and strategy files on a cold thread with a schema validator.
2. Reject unknown fields by default. An explicit schema extension is allowed,
   but typos must never be ignored.
3. Canonicalize content, calculate SHA-256, verify the approved signature in
   production, and assign a monotonically increasing configuration generation.
4. Fetch venue instrument/account metadata through bootstrap HTTP clients.
5. Compare product kind, base/quote/settlement/collateral, price/amount scale,
   tick, lot, minimum/maximum, multiplier, expiry, lifecycle, account mode, and
   fee source against the certified definition.
6. Resolve external names to dense IDs and build fixed arrays for instruments,
   accounts, strategies, models, subscriptions, and reverse dependencies.
7. Calculate all required capacities. Startup fails if configured counts exceed
   deployment maxima; the application never resizes after arming.
8. Publish the immutable catalog only after all validation succeeds.

### Runtime layout

- `InstrumentDefinition[] byInstrumentId`
- `StrategyRuntimeDefinition[] byStrategySlot`
- flattened `strategiesByInstrumentId` offset/count arrays
- model registries indexed by small integer ID
- venue/account/session bindings indexed by dense IDs

The hot representation SHOULD use flattened primitive arrays rather than an
object graph where a virtual call would occur per level or risk check. A
model-level virtual call is acceptable only if JMH and compiler inspection show
stable inlining; otherwise use explicit registry dispatch.

### Activation rules

- Staging a definition does not make it tradable.
- Material changes create a new strategy version and configuration generation.
- Existing execution groups remain bound to the generation that created them.
- A new generation may become active only when capacity exists and the previous
  generation has no unresolved exposure, unless a reviewed migration supports
  coexistence.
- Rollback activates a previously signed generation; it is never an in-place
  edit.

### Low-latency decision

All parsing, hashing, signature checks, name lookup, and capacity planning occur
before publication. The core receives one bounded activation command containing
IDs and a generation; it performs no file or map lookup.

## 4. Protocol and event envelope

SBE is the durable/process-boundary schema. Agrona ring records use the same
field semantics and numeric IDs but MAY use smaller lane-specific fixed layouts.
There is no generic object envelope, Java serialization, or JSON inside the
system boundary.

### Common envelope

Every durable event contains, in this logical order:

```text
schemaId, schemaVersion, templateId, blockLength
eventType, eventSequence, producerId, producerEpoch
cellId, venueId, accountId, instrumentId, strategyId
configurationGeneration, sessionGeneration, correlationId
exchangeEpochNanos, localReceiveEpochNanos, localReceiveMonoNanos
causeEventSequence, flags, reasonCode
```

Fields that do not apply use the schema null value. IDs are numeric; external
IDs are encoded only on venue-bound or audit events with strict maximum lengths.

### Sequence domains

- `eventSequence` is monotonic per producer epoch, not globally synchronized.
- `producerEpoch` changes on producer restart and fences delayed events.
- Venue sequence/change IDs remain separate fields; they are never normalized
  into a fabricated cross-venue sequence.
- `causeEventSequence` links a decision or command to its trigger. Opportunities
  also carry both book evidence epochs/sequences.
- Journal position is assigned by the recording stream and is not a business ID.

### Schema evolution

- Existing field IDs and meanings never change.
- Backward-compatible fields are appended with `sinceVersion`.
- A mandatory semantic/structural change gets a new template ID.
- Composite changes require a new composite/message contract.
- CI generates current and previous codecs and runs golden compatibility tests.
- Variable data is avoided in hot messages. When unavoidable it appears last
  and has a maximum validated before encoding.

Encoding uses `tryClaim`, writes into the claimed buffer, and commits only after
validation. A producer MUST abort an incomplete claim and MUST NOT spin forever
on a failed claim.

## 5. Clocks, IDs, and deadlines

### Clocks

- `NanoClock` supplies process-local monotonic time for deadlines and latency.
- `EpochNanoClock` supplies receive/audit time, not elapsed-time decisions.
- Venue timestamps are separate facts.
- The simulator injects both clocks. Production code MUST NOT call JVM clocks
  outside the clock adapters.
- Cross-host latency is reported only with a measured clock-error bound.
- Single-cell cross-leg skew compares the two books' last successfully applied
  receive timestamps from the same `NanoClock`. It MUST NOT subtract Bybit and
  Deribit exchange timestamps or monotonic timestamps from different hosts.

### IDs

The canonical local order ID is a 128-bit pair of primitive longs containing
cell, venue, session generation, strategy slot, and sequence. Each venue adapter
encodes it into the venue's bounded client-ID/label format and stores the exact
encoded bytes. Encoding is prevalidated and uses a reusable ASCII buffer.

IDs are never reused across session generations. A check character MAY detect
truncation but is not authentication.

### Deadlines

The core owns a preallocated primitive deadline wheel for opportunity expiry,
order acknowledgement, cancel, hedge, UNKNOWN escalation, cooldown, and stale
input deadlines. Timer callbacks use slot plus generation, not objects. Maximum
expiries per duty cycle are bounded so a timer storm cannot starve private fills.

## 6. Network transport and connection agents

### Socket separation

Each venue uses separate connections for public market data, authenticated order
entry, private order/execution/position data, and bootstrap/reconciliation HTTP.
Bybit trade and private streams are separate. Deribit order entry and private
subscriptions default to separate sockets; sharing requires evidence that it
cannot create head-of-line blocking.

### Netty construction

- Use Netty 4.2 with pooled direct `ByteBuf` at the network boundary.
- Production Linux starts with native `epoll`; `io_uring` is admitted only after
  a target-host spike proves better tails and sufficient maturity.
- One event loop owns a channel. Do not offload parsing to a generic executor.
- Bound WebSocket frame length, HTTP response length, decoder cumulation, and
  pending outbound bytes.
- Release every reference-counted buffer on every path. Use paranoid leak
  detection in tests, not production.
- Set TCP options from a measured profile. `TCP_NODELAY` is expected for order
  sockets; buffer sizes are measured.
- TLS reuse, DNS, proxying, compression, and certificate validation are explicit.
  WebSocket compression is off unless a venue requires it.

### Connection state

```text
STOPPED -> CONNECTING -> TLS -> AUTHENTICATING -> SUBSCRIBING -> LIVE
             ^                                             |
             +---- BACKOFF <- DEGRADED <- DRAINING <-------+
```

Reconnect uses exponential backoff with jitter and a cap; it never loops on the
event loop. A new live connection gets a new `sessionGeneration` and cannot
inherit unresolved old-generation orders without reconciliation.

Heartbeat scheduling follows the venue contract. Missing server activity or a
required test response disables new exposure and reconnects. Heartbeat success
does not prove private-stream completeness.

## 7. Venue parsers and normalization

Implement a venue-specific streaming JSON tokenizer over bytes. It tracks
depth, field hashes plus byte comparison, token offsets, and required-field
bitsets. It writes primitives into a claimed ring record or reusable scratch
structure; it does not build a JSON tree.

The parser MUST:

- tolerate arbitrary field order and documented unknown fields;
- reject duplicate required fields;
- enforce ASCII and maximum lengths for identifiers/numbers;
- accept only the certified numeric grammar per field;
- reject NaN, infinity, unsupported exponent notation, scale loss, and overflow;
- validate side, action, product, and status enums explicitly;
- bound array counts before iterating levels; and
- finish structural validation before committing the ring record.

`ByteBuf` references never cross into the core. Needed bytes are copied into the
bounded record before the inbound buffer is released.

### Normalized market event

```text
venue/instrument/session generation
feed profile and event kind (image, snapshot, delta, reset)
venue timestamps and all venue sequence fields
local receive timestamps
bid count and bounded (priceTicks, quantityLots) pairs
ask count and bounded (priceTicks, quantityLots) pairs
validation flags
```

Normalization converts syntax and units only. It MUST NOT invent sequencing,
fill missing fields, silently sort invalid data, or decide book trust.

- Bybit preserves `u`, `seq`, `ts`, and `cts`. A repeated snapshot replaces
  state; no exact `u + 1` rule is assumed without wire evidence.
- The Deribit bounded `book.<instrument>.none.<depth>.<interval>` profile is
  admitted only after Phase 0 captures prove the complete top-N image semantics
  expected by this design. Full incremental book messages require another
  adapter and are rejected here.
- Deribit native amount conversion comes from `InstrumentDefinition`, never
  symbol text.

Raw capture is a separate bounded sink. Public capture may drop with an explicit
gap counter but cannot block parsing. Private capture is disabled by default;
certification capture is redacted, encrypted, and access-controlled.

## 8. In-process messaging and core duty cycle

### Lane topology

Use one cache-line-aligned Agrona `OneToOneRingBuffer` per actual SPSC lane:

- one market-data ingress lane per public connection agent;
- one private/order-result ingress lane per authenticated/private agent;
- one control ingress lane;
- one normal order-command lane per venue order agent;
- one urgent order-command lane per venue order agent;
- one critical journal lane and one lossy telemetry lane.

Combining producers into a many-to-one queue is rejected because it introduces
contention and weakens source-specific overload containment.

### Lane sizing and overflow

Capacity is a power of two derived from measured peak rate, largest approved
pause, record size, and a safety factor:

```text
requiredRecords = peakRate * toleratedPauseSeconds * safetyFactor
requiredBytes   = requiredRecords * measuredP99EncodedRecordBytes
```

The deployment profile records the workload, size, occupancy alarm, maximum
event age, and failure policy. Huge queues are not inherently safe because they
turn market data into stale data.

Because the data ring itself may be full, each producer also owns a
cache-line-padded atomic `LaneHealthWord`. On failed critical publication it
release-stores `OVERFLOW`, producer epoch, and affected scope, stops normal
production, and resets/reconnects. The core acquire-reads all health words every
duty cycle before new exposure. The word carries health only, not domain data.

### Core priority and fairness

One bounded duty cycle performs:

1. Sample lane health and global kill/fault words.
2. Drain urgent control commands such as kill and disarm.
3. Drain private fills/order updates and order-send results to a high quota.
4. Advance resulting urgent hedge/cancel/unwind commands.
5. Drain market data round-robin per lane with per-lane quotas.
6. Immediately evaluate strategies affected by each applied trusted book event.
7. Advance normal order commands and remaining OEMS work.
8. Expire a bounded number of timers.
9. Publish critical journal events, then best-effort telemetry.

The loop repeats while work exists. Kill state and private traffic have priority,
while quotas and round-robin prevent monopolization. Health/kill words are
sampled even under continuous ring load. Exact quotas and idle strategy are
measured configuration. Busy-spin is allowed only on an isolated production
core; dev/test uses backoff.

Book, pricing, strategy, risk, and OEMS call directly on the core because they
share ownership. Rings between them would add serialization and queueing without
adding isolation.

## 9. Order books and trust

Each side is a fixed-capacity structure:

```text
long[] priceTicks
long[] quantityLots
int size
```

Bids descend and asks ascend. Arrays are allocated at startup. Book state also
contains epoch, venue sequence evidence, receive/exchange times, trust, and
reason code.

Image feeds use two preallocated array sets. The core validates and writes the
inactive set fully, then swaps the active index, so readers never see a partial
image. Delta feeds mutate only the active set after validation.

### Mutation rules

- At approved depths up to 64, start with branch-light linear search plus bounded
  `System.arraycopy`; benchmark against binary search on captured distributions.
- Quantity zero deletes only where the venue contract defines it.
- New prices insert sorted; known prices update in place.
- Duplicate prices, invalid ticks/lots, negative quantity, excess levels, or
  crossed state invalidate the event/book instead of being repaired.
- Image application validates order, uniqueness, bounds, and crossing before
  swap. It never sorts an invalid image into plausibility.

The query API returns primitives only: best level, indexed level, quantity
through price, executable quantity/notional, and VWAP/worst price into
caller-owned result slots. It returns no iterators, streams, collections, or
snapshots.

### Trust lifecycle

```text
DISCONNECTED -> SYNCING -> WARMING -> TRUSTED
      ^            |          |          |
      +------------+----------+----------+
       malformed, gap, overflow, stale, reconnect, impossible state
```

`WARMING` requires configured valid updates/time after an image. Freshness is
checked at decision time with monotonic receive time. Trust loss increments the
book epoch, invalidates outstanding opportunities, and suspends dependent
strategies.

A test-only `TreeMap` model receives identical normalized events. After every
event both models agree on levels, queries, VWAP, trust, and rejection reason.

## 10. Pricing and economic inputs

Books provide executable prices/quantities. Independent immutable input slots
provide fees, funding, expiry/settlement, conversion, liquidity haircut, latency
risk, and safety reserve. Every slot includes value, source, effective/receive
time, expiry, and generation. Missing/expired mandatory input returns
`PRICE_NOT_CERTIFIABLE`; it never defaults to zero.

For each affected strategy and direction:

1. Determine maximum canonical exposure from both trusted books and risk maxima.
2. Translate the requested size into native quantities with payoff/hedge models
   and conservative rounding.
3. Walk opposite-side arrays for executable VWAP and worst price.
4. Normalize proceeds, cost, delta, fees, carry, conversion, slippage/haircut,
   latency risk, and reserve into the risk currency.
5. Apply conservative directional rounding at each boundary.
6. Emit only when edge, minimum size, duration/freshness, and policy gates pass.

Every result stores the full cost decomposition.

### Temporal-coherence gate

For the single-cell runtime, pricing calculates at the same decision time:

```text
legAAge = decisionMonoNanos - legALastAppliedReceiveMonoNanos
legBAge = decisionMonoNanos - legBLastAppliedReceiveMonoNanos
legSkew = abs(legALastAppliedReceiveMonoNanos
            - legBLastAppliedReceiveMonoNanos)
```

The opportunity is certifiable only when both ages and `legSkew` are within the
strategy/feed-profile limits. Limits are measured from captures and economics;
they are not a universal constant. A 20 ms feed and a raw feed cannot silently
inherit the same thresholds.

Passing this gate does not prove both exchanges generated their books at the
same real-world instant; it bounds asymmetry in what this process most recently
observed. Venue timestamps remain diagnostic evidence and may tighten analysis
only when their clock-error bounds are independently established.

Skew failure returns `PRICE_NOT_TEMPORALLY_COHERENT`. It does not invalidate
either book because both may remain internally trustworthy. A book's trust
state changes only for its own gap, staleness, malformed data, or session event.

The opportunity records decision time, both receive times/ages, calculated skew,
configured age/skew limits, book epochs/sequences, and feed-profile IDs. This is
recomputed immediately before risk reservation; a previously passing opportunity
cannot be reused after either book evidence tuple changes.

### Low-latency decisions

- Evaluate only the changed instrument's reverse-dependency slice.
- Evaluate immediately after a trusted update; no timer batch or reactive
  pipeline in v1.
- Reuse caller-owned result slots.
- Precompute invariant scale factors and fee multipliers at load time.
- Use integer rational arithmetic with checked wide intermediates. If configured
  limits cannot fit, use a reviewed fixed-width 128-bit helper, not floating point.
- Floating point is permitted only for offline research/metrics, never order
  authorization.

An opportunity contains both book epochs/sequences/receive times, per-leg ages,
cross-leg skew and limits, feed/economic generations, configuration generation,
decision time, expiry, native quantities, worst prices, maximum exposure, and
cost decomposition. Risk revalidates them just before reservation. Opportunities
are not queued after evidence changes.

## 11. Strategy runtime

A strategy model consumes a validated definition plus read-only book/economic
views. It may propose an `ExecutionIntent`; it cannot reserve risk, allocate IDs,
or communicate with a venue.

The reusable basis model evaluates buy Bybit/sell Deribit and sell Bybit/buy
Deribit. Direction, initiating venue, horizon, thresholds, size ladder, cooldown,
and execution-policy ID come from the definition.

Each strategy slot has preallocated primitive state for lifecycle, generation,
last evidence, cooldown, active group count, P&L, kills, and metrics. No strategy
may access another strategy's mutable slot. Shared books are read-only. Positions
and risk stay strategy-attributed; portfolio netting is deferred.

The runtime suppresses an intent when its evidence tuple and policy output match
the last tuple, or active/cooldown/UNKNOWN rules prohibit a group. Deduplication
is not based on time alone.

## 12. Pre-trade and runtime risk

Risk uses fixed arrays indexed by global, venue, account, instrument, strategy,
and group scope. Ledgers track confirmed position, pending maximum fill,
reserved collateral/notional, unhedged exposure, realized P&L, daily loss, and
generation.

```text
AVAILABLE -> RESERVED -> PARTIALLY_CONSUMED -> RELEASED
                   \-> EXPIRED (only before any send)
```

Once a child may have reached a venue, maximum remaining fill stays reserved
until authoritative terminal evidence or reconciliation.

Risk applies the architecture's fixed check order and stops at first failure. It
rechecks evidence/generation/expiry, trust/freshness, cross-leg skew, session,
native quantity, price bands, exposure, collateral, hedge capacity,
duplicate/runaway limits, rate capacity, and hedge-path health in the same core
call that reserves.

Risk-reducing is calculated by risk from worst-case exposure before and after,
including possible outstanding fills. Strategy code cannot assert it.

Rate capacity is partitioned into normal initiation, hedge, and cancel/emergency.
Normal initiation cannot borrow emergency capacity. Venue feedback overrides
local optimism. An unknown rate state stops initiation.

Kills are monotonic within a generation until authorized reset. They stop
exposure-increasing actions, invalidate unsent reservations, and schedule
cancels. Hedge/unwind still must prove risk reduction and emergency bounds.

### Hedge-path health

Each hedge venue/session exposes one core-owned state:

```text
HEALTHY -> DEGRADED -> UNSAFE -> RECOVERING -> HEALTHY
```

Inputs are urgent-ring oldest age/occupancy, failed claims, order-agent progress,
socket/session state, local/venue rate capacity, UNKNOWN age, and recent
private-fill-to-transport-write p99/p99.9 against the certified profile. State
uses hysteresis so one sample cannot flap trading permission.

Only `HEALTHY` permits new exposure. `DEGRADED` and `UNSAFE` preserve urgent
cancel/hedge/unwind processing and reserved rate/lane capacity. Recovery requires
a minimum healthy observation window plus reconciliation when session integrity
was uncertain. The strategy cannot override this state.

## 13. OEMS, execution groups, and order tables

Child orders and groups live in fixed struct-of-arrays tables. References are
`(slot, generation)` so delayed events cannot mutate reused slots. A primitive
stack owns free slots.

External venue/execution IDs use fixed byte slabs plus offset/length and a
64-bit hash. Equality verifies bytes after hash. Bounded open-address tables have
a configured maximum load factor and never resize.

### Command lifecycle

1. Strategy proposes current-evidence intent.
2. Risk validates and reserves worst-case exposure.
3. OEMS allocates group/child slots and emits state facts.
4. Core publishes a normal order command.
5. Venue agent reports `WRITE_ACCEPTED`, `WRITE_FAILED`, or `WRITE_AMBIGUOUS`;
   successful socket write is not venue acceptance.
6. Venue/private facts advance state idempotently.
7. Each initiating fill delta recalculates/reserves hedge and publishes urgently.
8. Terminal resolution releases only capacity no longer able to fill.

An ack timeout, disconnect during write, uncorrelated private fact, conflicting
terminal fact, or incomplete reconciliation enters `UNKNOWN`/`RECONCILING`.
Never blindly resend the logical order. Search by client ID, venue ID, executions,
and position. Keep maximum exposure reserved and stop affected initiation.

### Partial fills

- Hedge from incremental confirmed fill, not order amount or ack.
- Cumulative fills are monotonic and bounded by order quantity.
- Duplicate execution is ignored only after exact identity/content match;
  conflicting content faults the scope.
- Conservative hedge rounding carries residual dust explicitly.
- The pay-up ladder bounds attempts, worst price, time, and unwind/escalation.
- Urgent lanes drain before normal initiation.

Transition tables in test resources drive exhaustive tests. Production code is
explicit Java; no reflection-heavy state-machine framework.

## 14. Venue order and private-data gateways

Adapters translate wire messages to common order/private facts and own auth,
serialization, correlation, rate feedback, session state, and reconciliation.
They do not decide strategy, pricing, or risk.

Outbound agents drain urgent commands first, serialize into reusable buffers,
validate session generation, write/flush, and publish write results. Urgent
commands flush immediately. Normal commands also default to immediate flush;
micro-batching needs tail-latency evidence.

### Bybit

- Use the V5 category endpoint, private WebSocket, and trade WebSocket.
- `reqId` and `orderLinkId` are unique in documented scopes and lengths.
- Command response proves request acceptance only; order/execution streams prove
  lifecycle and fills.
- Merge fast and full execution streams by execution identity without
  double-counting. Fast data may accelerate hedge but cannot erase later detail.
- Preserve `seq` with documented symbol/category scope.
- HTTP is metadata/reconciliation, not the normal order path.

### Deribit

- Use authenticated JSON-RPC WebSocket order entry with monotonic per-session IDs.
- Correlate out-of-order responses by ID.
- Labels carry deterministic external IDs; retain order/trade documented scopes.
- Subscribe narrowly to required private order/trade/portfolio/position channels.
- Token refresh, `test_request`, cancel-on-disconnect, and ownership are states.
- Amount conversion comes from the payoff definition because product units vary.

Reconciliation reads bounded pages on warm threads, normalizes them, and
publishes bounded facts. Lookback derives from the oldest unresolved order; no
unbounded history scan runs on core or order sockets.

## 15. Journal, snapshots, and recovery

The venue is authoritative for actual execution; the journal is authoritative
for local decisions, reservations, and observed facts. History is append-only.
Reconciliation emits compensating facts and never edits history.

### Publication classes

- **Critical:** order intent/command, write result, ack, fill, position,
  reservation/group transition, kill, reconciliation.
- **Important:** book trust, opportunity, risk rejection, config/session change.
- **Lossy:** debug samples and high-cardinality diagnostics.

Critical/important lanes are lossless under the certified workload. High-water
marks disarm before capacity exhausts. The critical reserve covers every
possible terminal and risk-reducing transition for maximum outstanding orders
after initiation stops. Telemetry may drop with counters.

Asynchronous durability cannot guarantee that the last in-memory tail survives
host/storage failure without adding an fsync round trip. V1 chooses asynchronous
local durability plus mandatory venue reconciliation: no silent application
queue loss under certified load; after crash, venue state resolves an unrecorded
tail. Risk approval and runbooks must state this limitation.

The journal agent encodes SBE into Aeron claims; Archive records on dedicated
storage. Publication failure degrades journal health and stops initiation while
preserving risk-reducing capacity. Segment size, retention, watermark, sync, and
checksums are explicit deployment settings.

Snapshots are versioned/checksummed at a journal position and contain catalog
generation, slot generations, positions, reservations, groups/orders including
UNKNOWN, session generations, risk/kills, and replay counters. Books always
resynchronize live. The core copies bounded fields to a preallocated buffer; a
cold writer writes, syncs, verifies, then atomically renames.

```text
BOOT -> SNAPSHOT_LOADED -> JOURNAL_REPLAYED -> PRIVATE_CONNECTED
     -> ORDERS_RECONCILED -> POSITIONS_RECONCILED -> BOOKS_TRUSTED
     -> WARMED -> DISARMED_READY -> ARMED
```

Any mismatch blocks progression. Production v1 never auto-arms.

## 16. Simulator, replay, and reference models

The simulator uses virtual clocks, stable event ordering, and recorded seeds.
Given artifact, config, input hashes, and seed it emits the same command/event
stream and terminal digest. Equal virtual times order by `(sourcePriority,
producerId, producerSequence)`; this is a simulation rule, not real causality.

Fake venues inject latency/jitter, partial/reordered/duplicate fills,
accept-with-lost-response, delayed reject, disconnects at every state, rate
changes, time skew, book gaps/images/malformed frames, and restart disagreement.

Paper fills are conservative and never claim queue position. Marketable orders
fill only against observed depth with configured adverse latency/slippage and
are labeled simulated.

Replay modes are raw-parser, normalized-book, decision, execution, recovery, and
full shadow. Every mode produces a stable digest and invariant report.

## 17. Operator control and lifecycle

V1 has one execution-cell JVM. Operator HTTP/CLI handling MAY live on cold
threads in that JVM, but every command crosses the bounded control lane. If it
moves to a separate process, the same SBE command/result runs over Aeron IPC.
There is no synchronous RPC from core.

Commands are status, stage config, activate, arm, disarm, scoped kill,
cancel-all, reconcile, snapshot, and controlled shutdown. Mutations carry
operator identity/role, unique ID, expected generation, scope, reason, issue
time, and expiry. Authentication happens before ingress; the core revalidates
state and generation. Duplicate IDs return the prior result; expired commands
fail.

Shutdown is an ordered state machine: disarm, stop new configuration/exposure,
resolve or cancel orders to a deadline, reconcile, snapshot, flush journal,
close order/private sockets, then market data/infrastructure. If deadline expires,
the emergency runbook makes an explicit choice; code cannot pretend force-stop
and continued risk handling are both safe.

## 18. Observability and performance evidence

The hot path increments preallocated counters and records primitive timestamps
into bounded histogram/telemetry buffers. Cold consumers export and render
bounded ID labels.

Mandatory signals include lane occupancy/failure/oldest age; connection/session
state; book epoch/freshness/invalidations; opportunity/rejection reason;
positions/pending/reservations/imbalance/UNKNOWN age; rate reserves; archive lag,
disk and snapshot age; core/agent progress; allocations/GC; and receive-decode,
decode-apply, apply-decision, decision-enqueue, enqueue-wire, and fill-hedge
latency distributions.

### Stage timestamp contract

For an opportunity/order path, carry a trace ID and record:

```text
T0  kernel/NIC receive timestamp when supported (optional evidence)
T1  Netty channel-read callback start
T2  venue decode/validation complete
T3  ingress ring commit
T4  core dequeue
T5  book apply/trust complete
T6  executable pricing complete
T7  strategy decision complete
T8  risk/OEMS reservation complete
T9  outbound ring commit
T10 order agent dequeue
T11 serialization/signing complete
T12 Netty write/flush invoked
T13 transport write completion/acceptance
T14 kernel/NIC transmit timestamp when supported (optional evidence)
T15 venue acknowledgement
T16 venue fill
```

`T13` proves only local transport completion, not exchange receipt, NIC emission,
or order acceptance. `T15` is not a fill. Optional hardware timestamps carry a
capability flag and clock-domain/error metadata.

For a maker or initiating fill, the mandatory economic path begins at the
private-stream `T1` equivalent and ends at hedge `T12/T13`. Reports separate
private decode, fill application/OEMS, urgent queue, serialization/signing, and
write segments. The primary gates are:

```text
marketDataToTransportWrite = orderT13 - marketDataT1
privateFillToHedgeWrite    = hedgeT13 - privateFillT1
urgentQueueAge             = hedgeT10 - hedgeT9
```

Phase 0/14 certifies p99 and p99.9 budgets per venue/feed/order profile. Core
latency remains a sub-budget, not a substitute for the end-to-end gate. Runtime
breaches feed `HedgePathHealth`; they are not monitoring-only alerts.

The core never formats log text. It emits compact diagnostic facts. Cold
consumers rate-limit and format them. Secrets and unredacted private payloads are
never logged.

- Use JMH for isolated algorithms and end-to-end burst replay as release gate.
- Use coordinated-omission-aware histograms where applicable.
- Store host, CPU, kernel, JVM/flags, artifact/config/input hashes, power state,
  and background load with results.
- Compare p50, p99, p99.9, p99.99, max, throughput, allocation, and queue age.
- Approve regression budgets per component; do not hide tails with medians.

## 19. JVM and host profile

- Java 25 LTS, fixed `-Xms`/`-Xmx`, and heap pre-touch in production.
- Begin with G1 because hot code is zero-allocation and G1 is conventional;
  compare ZGC on the target host and decide from tail evidence.
- Size heap so steady-state collection is unnecessary during a session while
  leaving reconnect/reconciliation headroom. Bound direct memory separately.
- No finalizers, dynamic agents, runtime instrumentation, or live heap histogram.
- Use a reviewed low-overhead JFR profile; intensive recording is test-only.
- Linux performance governor, explicit C-state/turbo policy, thermal monitoring.
- Isolate physical cores only after measurement; keep siblings/noisy IRQs away.
- Align RSS/RPS/XPS, IRQ, NUMA, affinity, and archive storage to host topology.
- Chrony/NTP is mandatory; PTP/hardware timestamps are evidence-driven later.
- No swapping for the execution JVM. Validate file descriptor, memory, and
  socket limits before arm.
- Containers may package but not hide CPU/NUMA/network/clock/disk controls.

## 20. Security boundaries

- Credentials are venue/account/session scoped, withdrawal-disabled,
  IP-allow-listed, and injected into only the owning adapter.
- Secrets never enter domain events, captures, logs, command lines, or dumps.
- Raw public authentication uses a distinct read-only key when required.
- Reconciliation clients receive only required read/order-management scopes.
- Operator control uses mutual authentication, role authorization, expiry,
  replay protection, and complete audit.
- Artifacts include SBOM/provenance. Dependency changes require replay, fuzz,
  security, and performance validation.

## 21. Component failure matrix

| Failure | Immediate behavior | Recovery condition |
|---|---|---|
| Public feed disconnect/malformed/gap/overflow | invalidate books/opportunities; stop affected initiation | new session/image, warm-up, fresh economics |
| Both books trusted but age/skew gate fails | reject combined opportunity; retain individual book trust | a new evidence pair within certified age/skew limits |
| Private stream disconnect/overflow | stop account/venue initiation; reserve possible fills | reconnect and order/fill/position reconciliation |
| Order socket ambiguous write | child UNKNOWN; no resend | authoritative private/query resolution |
| Normal order lane full | reject/stop initiation | healthy occupancy and explicit recovery |
| Urgent order lane unavailable | venue/global fault and alert | restored capacity/session plus reconciliation |
| Hedge-path latency/queue health degraded | stop new exposure; retain cancel/hedge/unwind | healthy observation window and any required reconciliation |
| Journal high water/unavailable | stop initiation; retain reducing reserve | archive caught up and health cleared |
| Telemetry full | drop with counter | consumer recovery; trading unchanged |
| Stale fee/funding/conversion | stop dependent strategy | fresh versioned input |
| Risk arithmetic overflow | reject and fault strategy | corrected definition/limits, new generation |
| Core watchdog stall | supervisor alert; certified CoD only | restart, replay, reconcile, manual arm |
| Disk watermark | stop initiation before archive failure | operator/retention action, archive healthy |
| Clock-sync breach | stop strategies relying on epoch bounds | error returns inside approved bound |

## 22. Build order and component gates

| Component | Evidence required before dependents proceed |
|---|---|
| Catalog/domain | exact units, capacity proof, metadata mismatch tests |
| Protocol | current/previous compatibility and golden binary fixtures |
| Parser | fuzz, fragmentation, corpus replay, zero-allocation profile |
| Rings/scheduler | overflow/fairness tests and burst queue-age report |
| Book/trust | differential/property tests and mutation/VWAP benchmarks |
| Pricing/strategy | BigDecimal oracle, boundary tests, evidence-complete decision |
| Risk/OEMS | executable model/state-space tests, temporal-coherence and hedge-path-health invariant report |
| Venue gateways | fake-venue then testnet fault matrix |
| Journal/recovery | kill-point, corrupt snapshot, replay/reconcile tests |
| Application/control | lifecycle, authorization, watchdog, runbook rehearsal |
| Whole cell | 24-hour/7-day soak, shadow holdout, target-host stage and end-to-end latency gates |

## 23. Decisions intentionally deferred

Implementers MUST NOT choose these silently:

- exact ring sizes, quotas, and idle strategies: Phase 3/14 measurement;
- G1 versus ZGC and epoll versus io_uring: target-host Spike S4;
- first production strategy/model catalog: Phase 0 evidence;
- control endpoint same-process versus sidecar: default same JVM/cold threads;
- synchronous pre-order durability: not v1; revisit if risk approval requires it;
- full-depth/Starbase/MMWS books: separate adapter/book ADR and benchmarks;
- passive maker and multi-cell execution: follow
  [Advanced Execution Design](advanced-execution-design.md) after separate ADR,
  evidence, and implementation plan;
- portfolio netting: separate post-v1 architecture.

## 24. Implementation review checklist

For every component PR, reviewers answer:

- Who is the sole writer of every mutable field?
- What bounds memory, queue, message, loop, retry, and history?
- What event makes the component distrust state?
- What happens when output cannot publish?
- Are units and rounding explicit?
- Can an old-generation event mutate current state?
- Is an acknowledgement mistaken for execution truth?
- What allocation, syscall, lock, exception, or formatting remains hot?
- Which fixture/property/model/fault test proves failure behavior?
- Which histogram and queue-age evidence proves latency?
- Does a cross-leg calculation prove both per-leg age and same-clock receive
  skew, and preserve the evidence in the opportunity?
- Does any hedge-path latency breach change risk permission, or merely alert?
- Can replay reproduce the decision from stored evidence/configuration?
- Is rollback/reconciliation safe?

"The venue/library normally handles it" is not sufficient. The contract,
observed behavior, and local failure policy must be explicit.

## 25. Primary implementation references

- [Bybit public order book](https://bybit-exchange.github.io/docs/v5/websocket/public/orderbook)
- [Bybit server-location FAQ](https://bybit-exchange.github.io/docs/faq)
- [Bybit order and post-only semantics](https://bybit-exchange.github.io/docs/v5/order/create-order)
- [Bybit WebSocket connection and heartbeat](https://bybit-exchange.github.io/docs/v5/ws/connect)
- [Bybit WebSocket order entry](https://bybit-exchange.github.io/docs/v5/websocket/trade/guideline)
- [Bybit private execution stream](https://bybit-exchange.github.io/docs/v5/websocket/private/execution)
- [Deribit bounded aggregated book](https://docs.deribit.com/subscriptions/orderbook/bookinstrument_namegroupdepthinterval)
- [Deribit market-data practices](https://docs.deribit.com/articles/market-data-collection-best-practices)
- [Deribit order-management practices](https://docs.deribit.com/articles/order-management-best-practices)
- [Agrona `OneToOneRingBuffer`](https://github.com/aeron-io/agrona/blob/master/agrona/src/main/java/org/agrona/concurrent/ringbuffer/OneToOneRingBuffer.java)
- [Aeron best-practices guide](https://github.com/aeron-io/aeron/wiki/Best-Practices-Guide)
- [SBE design principles](https://github.com/aeron-io/simple-binary-encoding/wiki/Design-Principles)
- [SBE message versioning](https://github.com/aeron-io/simple-binary-encoding/wiki/Message-Versioning)
- [Netty native transports](https://netty.io/wiki/native-transports.html)
- [Netty reference-counted objects](https://netty.io/wiki/reference-counted-objects.html)
