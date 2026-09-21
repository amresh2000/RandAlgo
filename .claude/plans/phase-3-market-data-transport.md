# Feature: Phase 3 market-data transport, capture, and codecs

This plan implements the Phase 3 feed boundary on top of the Phase 2 exact-domain and protocol branch. Validate the referenced venue documentation and captured fixtures again before promoting any feed profile to production.

## Feature Description

Build bounded Bybit and Deribit public market-data ingestion: transport-neutral lifecycle contracts, fixed-capacity SPSC publication, an independent overflow/fault signal, allocation-controlled byte parsers, reconnect/heartbeat/subscription state machines, cold-path raw capture and deterministic replay, and a fair core ingress skeleton.

Phase 3 makes venue messages safe and deterministic enough for Phase 4 books. It does not decide whether a book is economically actionable; it preserves evidence and makes malformed, disconnected, or dropped input impossible to mistake for trustworthy market data.

## User Story

As an OMS engineer, I want bounded venue feed sessions to normalize captured wire messages into deterministic primitive events so that the single-threaded core can consume market data without hidden loss, allocation pressure, fabricated sequencing, or producer starvation.

## Problem Statement

The repository has exact units, generated protocol codecs, clocks, and module boundaries, but venue modules contain only package declarations. There is no connection lifecycle, parser, publication lane, overflow signal, fixture replay, or fair ingress duty cycle. Phase 4 cannot safely maintain books until this boundary exists and proves that failure revokes trust rather than emitting plausible partial data.

## Solution Statement

Introduce a primitive `basis-venue-api` contract centered on a reusable `MutableMarketDataEvent`, a fixed-layout Agrona SPSC lane, a cache-line-padded health word, and explicit session/subscription/reconnect abstractions. Implement venue-specific streaming byte parsers and bounded Netty session handlers for Bybit and Deribit. Put capture/replay on cold threads and add a `CoreAgent` skeleton that samples all health words before draining lanes round-robin with quotas.

Deribit bounded top-N parsing is implemented and replay-tested but remains capability-gated until sanitized production captures establish that the selected `book.<instrument>.none.20.<interval>` notification is a complete image. Multi-day evidence is not required to build the adapter; it is required to certify and arm it.

## Out of Scope / Non-Goals

- Not included: Phase 4 book mutation, gap repair, trust promotion, executable depth, or strategy dispatch.
- Not included: private order/fill streams and order entry; their lane priority slots are represented only by the generic core API.
- Not included: secrets in source, logs, captures, arguments, or fixtures. Deribit credentials enter only through an injected credential/token provider.
- Not included: production calibration of ring sizes, idle strategy, native transport, socket buffers, or latency budgets; defaults are explicit test/dev bounds.
- Not included: claiming Phase 0 or economic certification complete. Current live-smoke evidence is short and raw wire JSONL is not committed.
- Not changing: Phase 2 SBE schema or durable golden frames. Phase 3 ingress uses an internal fixed binary layout and leaves durable schema evolution to a separately reviewed protocol change.

## Feature Metadata

**Feature Type**: New Capability  
**Estimated Complexity**: High  
**Primary Systems Affected**: `basis-venue-api`, `basis-venue-bybit`, `basis-venue-deribit`, `basis-sim`, `basis-app`  
**Dependencies**: Java 25, Netty 4.2.18.Final, Agrona 2.6.1, Phase 2 numeric/protocol contracts, sanitized venue fixtures

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 3  
**Epic**: `docs/implementation-plan.md`

**Back-references**:

- `.claude/plans/phase-2-protocol-exact-domain.md` - exact numeric, time, identity, protocol, and dependency decisions inherited by this phase.
- `.claude/reports/phase-0-feasibility-report.md` - records the short public-feed smoke run and the remaining production-evidence gap.

**Forward-references**:

- Phase 4 consumes the normalized lanes and establishes venue-specific book/trust lifecycle.

---

## CONTEXT REFERENCES

### Relevant Codebase Files — read before implementing

- `docs/implementation-plan.md:471` - normative Phase 3 tasks and exit gate.
- `docs/component-design.md:271` - Netty ownership, bounded frames, reference counting, and connection states.
- `docs/component-design.md:302` - streaming parser validation and normalized event fields.
- `docs/component-design.md:351` - SPSC topology, health-word publication, and fair core duty cycle.
- `docs/component-design.md:769` - receive/decode/commit stage timestamp contract.
- `docs/architecture.md:298` - thread ownership and transport boundary.
- `basis-protocol/src/main/resources/sbe/basis-messages.xml:150` - existing common event evidence and book fields; do not silently reinterpret them.
- `basis-core/src/main/java/com/penguinsecure/basis/core/numeric/AsciiDecimalParser.java:7` - certified decimal grammar and overflow/scale behavior to mirror for direct bytes.
- `basis-core/src/main/java/com/penguinsecure/basis/core/time/MonotonicClock.java:1` - injected monotonic time contract.
- `basis-app/src/test/java/com/penguinsecure/basis/architecture/ArchitectureRulesTest.java:32` - dependency and direct-clock constraints.
- `pom.xml:27` - pinned versions and Java/test/build rules.

### New Files to Create

- `basis-venue-api/.../marketdata/*` - event, source/sink, profile, session and failure contracts.
- `basis-venue-api/.../lane/*` - fixed-layout Agrona lane and padded health word.
- `basis-venue-api/.../session/*` - reconnect policy, subscription set, and lifecycle transition guard.
- `basis-venue-api/.../json/*` - bounded allocation-free byte cursor/token utilities shared by venue parsers.
- `basis-venue-bybit/.../marketdata/*` - Bybit endpoint selector, parser, protocol messages, and Netty session handler.
- `basis-venue-deribit/.../marketdata/*` - Deribit parser, JSON-RPC messages, token abstraction, and Netty session handler.
- `basis-sim/.../capture/*` - bounded raw capture sink and deterministic fixture replayer.
- `basis-app/.../CoreAgent.java` - fair ingress skeleton.
- Unit, replay, fuzz/property, fragmentation, and overflow tests adjacent to their modules.

### Relevant Documentation — read before implementing

- [Netty `WebSocketFrameAggregator`](https://netty.io/4.2/api/io/netty/handler/codec/http/websocketx/WebSocketFrameAggregator.html)
  - Constructor enforces a maximum aggregate length and throws `TooLongFrameException`; control frames pass through during fragmentation.
- [Netty `WebSocketDecoderConfig`](https://netty.io/4.2/api/io/netty/handler/codec/http/websocketx/WebSocketDecoderConfig.html)
  - Configure maximum payload, UTF-8 validation, protocol-close behavior, and extensions explicitly.
- [Netty reference-counted objects](https://netty.io/wiki/reference-counted-objects.html)
  - The final handler that consumes an inbound frame owns its release; no `ByteBuf` crosses the venue boundary.
- [Agrona 2.6.1 `OneToOneRingBuffer`](https://github.com/aeron-io/agrona/blob/2.6.1/agrona/src/main/java/org/agrona/concurrent/ringbuffer/OneToOneRingBuffer.java)
  - Backing capacity is power-of-two plus trailer; `tryClaim`/`commit` enables zero-copy publication and `read(..., limit)` enables quotas.
- [Bybit V5 order book](https://bybit-exchange.github.io/docs/v5/websocket/public/orderbook)
  - Preserve `u`, `seq`, `ts`, and `cts`; repeated snapshots replace state; `u=1` indicates service restart; field order differs by profile.
- [Deribit API documentation](https://docs.deribit.com/index.html)
  - JSON-RPC WebSocket endpoints and environment separation.
- [Deribit bounded book channel](https://docs.deribit.com/subscriptions/orderbook/bookinstrument_namegroupdepthinterval)
  - Approved candidate syntax is `book.<instrument>.none.20.<interval>`; production image semantics stay gated by captures.
- [Deribit authentication](https://deribit.mintlify.app/articles/authentication)
  - Inject client credentials, retain tokens only in cold session state, and refresh before `expires_in`; never capture token payloads.

### Patterns to Follow

**Naming conventions:** package `com.penguinsecure.basis`; no `I` interface prefix; numeric names carry units such as `priceTicks`, `receiveMonoNanos`, and `venueTimestampMillis`.

**Error handling:** hot parsers return enum status/reason codes and mutate caller-owned scratch; they do not throw for malformed venue input. Constructors throw for invalid static configuration.

**Time:** all production timestamps use injected `EpochClock`/`MonotonicClock`; direct JVM clock calls remain confined to Phase 2 system adapters.

**Hot/cold classification:** every new production package has `package-info.java` with `@path` and `@owner`. Parsing, lanes, session callbacks, and the core agent are HOT/WARM; file capture/replay configuration is COLD.

**Publication:** validate the complete message first, claim one record, encode primitives, stamp commit time, and commit. On insufficient capacity, release-store `OVERFLOW` to the independent health word before stopping normal publication.

---

## IMPLEMENTATION PLAN

### Phase A: Transport-neutral contracts and lanes

- Add Agrona and core clock dependencies to `basis-venue-api`.
- Define bounded feed profiles, event kind/status/failure enums, mutable event scratch, source/sink, lifecycle, and subscriptions.
- Implement a fixed-layout direct SPSC market-data lane and a separately padded `LaneHealthWord` with release/acquire semantics.
- Unit-test round-trip, capacity validation, full-ring signaling, health visibility, and no-commit-on-invalid input.

### Phase B: Shared bounded byte parsing

**Depends on:** Phase A.

- Implement a reusable byte cursor over Netty `ByteBuf`/Agrona `DirectBuffer` without JSON trees or `String` creation.
- Bound nesting, token lengths, identifier lengths, and level counts; skip unknown values structurally.
- Parse exact decimals directly from ASCII into scaled longs with the Phase 2 grammar and explicit error codes.
- Add corpus/property tests for field order, unknown fields, duplicate/missing fields, Unicode, exponent/NaN/infinity, scale loss, overflow, and truncation.

### Phase C: Bybit adapter

**Depends on:** Phases A-B.

- Select linear/inverse endpoints from explicit product-family metadata.
- Build bounded subscribe/ping payloads and subscription union state.
- Parse depth-50 snapshot/delta frames preserving all native timestamps and `u`/`seq`.
- Implement a Netty WebSocket session handler with max frame length, fragmentation aggregation, activity/heartbeat deadlines, reconnect transitions, session generations, and deterministic failure invalidation.
- Test captured fixtures, fragmented/coalesced delivery, repeated snapshots, `u=1`, malformed input, disconnect, and oversize handling.

### Phase D: Deribit adapter

**Depends on:** Phases A-B.  
**Independent of:** Phase C after the shared contracts/parser are complete.

- Model credential/token providers so secrets never enter events or captures.
- Build bounded JSON-RPC auth/refresh, heartbeat/test response, and subscription messages.
- Parse approved depth-20 bounded notifications preserving channel, timestamp, and native change identifiers.
- Keep publication disabled unless the explicit feed capability says complete-image semantics were capture-certified.
- Test fixture parsing, token refresh scheduling, reconnect/resubscription, heartbeat test requests, capability rejection, malformed input, and oversize handling.

### Phase E: Capture, replay, and fair core ingress

**Depends on:** Phases A, C, and D.

- Implement a separate bounded raw-frame capture queue and sanitized rotated writer on a cold thread; public drops increment an explicit gap counter.
- Implement fixture replay through the exact production parser entry points and a stable digest over normalized events.
- Add `CoreAgent` health-first sampling and bounded round-robin draining with separate high-priority and market-data quotas.
- Burst-test continuous producers, overflow, failed lanes, and starvation resistance.

### Phase F: Full gates and documentation reconciliation

- Run unit/property/replay/venue-contract profiles and the full reactor.
- Add allocation measurement after warm-up for parser entry points; fail the benchmark/release gate if allocations remain.
- Commit sanitized representative fixtures and hashes. Update capability docs only for facts proven by those fixtures; leave production certification explicitly pending otherwise.
- Write the Phase 3 implementation report and review deviations.

---

## STEP-BY-STEP TASKS

### 1. UPDATE module dependencies

- **IMPLEMENT**: add `org.agrona:agrona` and `basis-core` to venue API; add `io.netty:netty-buffer`, `netty-codec-http`, `netty-handler`, and `netty-transport` only to concrete venue modules; wire venue adapters into sim tests where needed.
- **GOTCHA**: Netty types must not appear in `basis-venue-api` public contracts.
- **VALIDATE**: `./mvnw -o -pl basis-venue-api,basis-venue-bybit,basis-venue-deribit -am test`
- **SATISFIES**: bounded primitive boundary and dependency rules.

### 2. CREATE venue API market-data/session contracts

- **IMPLEMENT**: explicit primitive configurations, states, failure reasons, subscription ownership, source/sink lifecycle, and caller-owned event scratch.
- **PATTERN**: exact unit naming in `InstrumentDefinition`; enum status returns in `AsciiDecimalParser`.
- **VALIDATE**: venue API unit tests plus `PackageClassificationTest`.
- **SATISFIES**: Phase 3 task 1.

### 3. CREATE Agrona market-data lane and health word

- **IMPLEMENT**: power-of-two direct buffer plus trailer, fixed maximum depth, fixed record codec, `tryClaim`/`commit`, bounded `read`, producer epoch/session/scope in a cache-line-separated health word.
- **GOTCHA**: health must remain observable when the ring is full; invalid input never commits.
- **VALIDATE**: ring round-trip/full/overflow/visibility tests.
- **SATISFIES**: Phase 3 tasks 6-7 and exit gate 4.

### 4. CREATE bounded JSON byte utilities

- **IMPLEMENT**: structural cursor and exact decimal parse from buffer ranges; duplicate required-field bitsets and bounded arrays.
- **GOTCHA**: no Jackson tree, regex, `String`, collection, or per-message object allocation on the parser path.
- **VALIDATE**: malformed/property corpus tests.
- **SATISFIES**: parser safety and zero-allocation architecture.

### 5. CREATE Bybit parser and protocol/session components

- **IMPLEMENT**: endpoint/profile mapping, payload writer, parser, heartbeat, reconnect, subscriptions, Netty frame limits and handler.
- **GOTCHA**: accept arbitrary field order; preserve `u`, `seq`, `ts`, `cts`; never assume exact `u+1` continuity.
- **VALIDATE**: Bybit unit, fragmentation, fixture replay, and venue-contract tests.
- **SATISFIES**: Phase 3 tasks 2-3 and native evidence preservation.

### 6. CREATE Deribit parser and protocol/session components

- **IMPLEMENT**: token abstraction, JSON-RPC IDs/results/notifications, heartbeat test response, reconnect/subscription state, bounded-depth parser, capability guard.
- **GOTCHA**: do not store/log credentials or tokens; no event publication before bounded-image certification flag is true.
- **VALIDATE**: Deribit unit, fragmentation, fixture replay, token lifecycle, and venue-contract tests.
- **SATISFIES**: Phase 3 tasks 2 and 4.

### 7. CREATE capture/replay components and fixtures

- **IMPLEMENT**: bounded public capture queue, explicit drop counter, sanitization, rotated output, production-parser replay, stable digest.
- **GOTCHA**: capture must not block the event loop; authentication payloads are never captured.
- **VALIDATE**: `./mvnw -o -Preplay-tests verify` and fixture checksum validation.
- **SATISFIES**: Phase 3 task 8 and exit gate 1.

### 8. CREATE fair `CoreAgent`

- **IMPLEMENT**: sample every health word first, drain priority lanes to configured quotas, then rotate market-data start index each duty cycle and drain per-lane quotas.
- **GOTCHA**: a continuously non-empty first lane cannot monopolize the duty cycle; any unhealthy word is observed before new event delivery.
- **VALIDATE**: deterministic fairness/overflow/burst tests.
- **SATISFIES**: Phase 3 task 10 and exit gate 4.

### 9. UPDATE docs and write report

- **IMPLEMENT**: fixture hashes, demonstrated semantics, remaining certification gap, usage/limits, implementation report.
- **VALIDATE**: Spotless check and full build.
- **SATISFIES**: auditable completion without overstating production readiness.

---

## TESTING STRATEGY

### Unit Tests

- Lifecycle legal/illegal transitions, reconnect saturation/jitter bounds, subscription deduplication/capacity.
- Lane record round-trip at 0/max depth, invalid counts, wrap, full publication, and independent health signal.
- Bybit/Deribit valid images/deltas, required/duplicate fields, native evidence fields, number grammar, and max bounds.
- Netty handler release behavior, fragmented frames, ping/pong/close interleaving, oversize exceptions, and disconnect invalidation.
- Deribit secret/redaction boundaries and capability gate.
- `CoreAgent` health-first ordering and quota fairness.

### Property/Fuzz Tests

- Mutate field order, insert unknown nested values, truncate each byte position, duplicate required fields, alter brackets/quotes/escapes, inject non-ASCII, and generate invalid decimals.
- Assert parser termination, no exception leak for wire errors, no event commit on rejection, and stable result for equivalent valid field orders.

### Replay/Integration Tests

- Feed sanitized raw fixtures through the same parser entry point as Netty and compare normalized event bytes/digest.
- Replay fragmented and coalesced logical messages.
- Fill a lane while the core is stalled and prove health visibility before any subsequent event.
- Continuously fill one market lane while low-rate lanes remain bounded-latency visible.

### Allocation and Performance

- Warm parser/session handlers, then measure allocations with JFR or thread allocation counters over fixture loops.
- Record throughput, p50/p99/p99.9 parse-to-commit time, ring occupancy, and input hash. The production hot parser target is zero allocated bytes per message after warm-up.

---

## VALIDATION COMMANDS

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -o spotless:check
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -o -T1C clean verify
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -o -Pproperty-tests verify
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -o -Preplay-tests verify
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -o -Pvenue-contract-tests verify
python3 -m unittest discover -s tools/tests -v
```

Live venue-contract tests remain opt-in and must use public endpoints or externally injected credentials; the default build never requires secrets or network access.

---

## ACCEPTANCE CRITERIA

- [ ] Primitive venue/session contracts expose no Netty types.
- [ ] Every frame/cumulation/array/token/subscription/ring/capture boundary has an explicit maximum.
- [ ] Both parsers preserve venue timestamps and sequence/change identifiers without inventing continuity.
- [ ] Equivalent fixture input replays to byte-identical normalized event digests.
- [ ] Parser rejection, disconnect, and lane-full paths publish independent unhealthy/overflow state and commit no plausible event.
- [ ] Core samples health before delivery and bounded round-robin tests prove no market lane starvation.
- [ ] Hot parser paths allocate zero bytes per message after warm-up under the approved measurement method.
- [ ] All project validation commands pass.
- [ ] Capability docs distinguish implemented behavior from production-certified behavior.

## COMPLETION CHECKLIST

- [ ] Tasks 1-9 complete in order.
- [ ] Unit/property/replay/venue-contract tests pass.
- [ ] Full clean Java 25 reactor passes.
- [ ] Fixture hashes and documentation are current.
- [ ] Phase 3 report records any deviations and remaining Phase 0 evidence gaps.

## OPEN QUESTIONS / ASSUMPTIONS

- **Assumption:** Build Deribit bounded depth-20 support behind a default-off certification capability. This is the recommended safe option because current committed evidence does not prove complete-image semantics.
- **Assumption:** Start with explicit conservative dev/test capacities and make every capacity constructor/config driven. Production values wait for representative captures.
- **Assumption:** Netty NIO is the portable implementation/test baseline; Linux epoll selection and socket tuning are Phase 14 deployment calibration.
- **Open evidence gap:** sanitized raw feed fixtures from the successful Phase 0 smoke run are not in git. Fresh small public fixtures may be captured for tests; seven representative production days remain a later certification gate.

## NOTES (open canvas)

The critical safety ordering is `receive -> fully parse/validate scratch -> claim -> encode -> timestamp -> commit`. Claiming before structural validation can hold ring capacity during malformed input; publishing before complete validation can leak a believable partial book event. The independent health word is deliberately not a ring message because a full ring cannot carry its own overflow report.

Confidence for one-pass implementation: **8/10**. The principal uncertainty is Deribit bounded-feed semantics, handled by the explicit capability gate rather than guessed behavior.

## AMENDMENTS

