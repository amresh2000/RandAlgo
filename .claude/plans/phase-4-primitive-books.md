# Feature: Phase 4 primitive books and trust lifecycle

This plan implements `docs/implementation-plan.md` Phase 4 on top of the Phase 3 normalized market-data boundary. Revalidate production capture assumptions before enabling any feed profile in production; implementation and hermetic correctness work do not promote uncertified Deribit image semantics.

## Feature Description

Build a fixed-capacity, allocation-free order book in `basis-core` with best-first primitive arrays, double-buffered image application, bounded delta mutation, primitive executable-depth queries, venue-evidence tracking, and a fail-closed trust/freshness lifecycle. Prove it against a test-only `TreeMap` reference model, randomized event streams, replay fixtures, allocation checks, and JMH benchmarks.

## User Story

As the single-writer execution core, I want normalized venue events converted into bounded trustworthy books so that later pricing and strategy phases can consume executable depth without mistaking malformed, stale, disconnected, or semantically invalid state for tradeable evidence.

## Problem Statement

Phase 3 safely normalizes and publishes venue evidence but deliberately does not mutate books or decide trust. Phase 5 cannot price opportunities until a deterministic component validates book structure and sequencing, maintains primitive depth, exposes allocation-free queries, and immediately revokes new-exposure permission on failure.

## Solution Statement

Add a core-owned `BookUpdateView` seam implemented by Phase 3's reusable event, a fixed-depth book with two preallocated array sets, explicit mutation/query result slots, configurable sequence modes, and an epoch-bearing trust tracker. Keep venue-specific transport outside core by configuring semantic modes at assembly. Add independent `TreeMap` differential tests and JMH coverage.

## Out of Scope / Non-Goals

- No strategy evaluation, cross-leg skew gate, fees/carry, or opportunity creation; those are Phase 5.
- No full-depth unbounded book, radix tree, or Deribit incremental `prev_change_id` channel.
- No production promotion of Deribit bounded images without representative captures.
- No runtime collections, iterators, snapshots, logging, clocks, or allocation on book mutation/query paths.

## Feature Metadata

**Feature Type**: New capability  
**Estimated Complexity**: High  
**Primary Systems Affected**: `basis-core`, `basis-venue-api`, `basis-sim`, `basis-benchmarks`, architecture tests  
**Dependencies**: Java 25, jqwik 1.10.1, JMH 1.37; no new dependency

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 4 / atomic task 8  
**Back-references**:

- `.claude/plans/phase-3-market-data-transport.md` - normalized primitive events, lane failure, and native evidence.
- `docs/architecture.md` sections 7-8 - fixed arrays, sequence evidence, trust lifecycle.
- `docs/component-design.md` section 9 - mutation, queries, double buffering, and reference model.

## CONTEXT REFERENCES

### Relevant Codebase Files

- `basis-venue-api/src/main/java/com/penguinsecure/basis/venue/api/marketdata/MutableMarketDataEvent.java` - reusable normalized event that will implement the core view.
- `basis-venue-api/src/main/java/com/penguinsecure/basis/venue/api/marketdata/MarketDataEventKind.java` - wire semantic mapping.
- `basis-core/src/main/java/com/penguinsecure/basis/core/product/InstrumentDefinition.java` - tick/lot constraints supplied to the book.
- `basis-core/src/main/java/com/penguinsecure/basis/core/numeric/CheckedDecimalMath.java` - checked query arithmetic.
- `basis-app/src/test/java/com/penguinsecure/basis/architecture/ArchitectureRulesTest.java` - core dependency and hot-path restrictions.
- `basis-benchmarks/src/main/java/com/penguinsecure/basis/benchmarks/BenchmarkHarnessSmoke.java` - JMH style and module wiring.

### New Files to Create

- `basis-core/.../book/*` - primitive contracts, book, trust/sequence state, caller-owned query result.
- `basis-core/src/test/.../book/ReferenceOrderBook.java` - test-only `TreeMap` oracle.
- `basis-core/src/test/.../book/*Test.java` and `*Properties.java` - examples, differential properties, and allocation gate.
- `basis-benchmarks/.../FixedDepthOrderBookBenchmark.java` - mutation and query microbenchmarks.
- `.claude/reports/phase-4-primitive-books-report.md` - execution report.

### Relevant Documentation

- [jqwik 1.10.1 user guide](https://jqwik.net/docs/current/user-guide) - package-scoped properties, jqwik tags, generated cases, and shrinking.
- OpenJDK JMH samples - state-scoped benchmark fixture patterns.

### Patterns to Follow

- Final classes, explicit constructors, primitive/caller-owned hot APIs, and early invariant rejection.
- `@path HOT` / `@owner core` package classification.
- Expected failures are status enums, not exceptions; constructor misuse may throw.
- Inject `nowMonoNanos`; never read JVM clocks in core.
- Use `net.jqwik.api.Tag` for property profile isolation.

## IMPLEMENTATION PLAN

### Phase 1: Core event and trust contracts

- Define update view/type, side, mutation status, trust state, rejection reason, sequence mode, and caller-owned executable result.
- Make the Phase 3 mutable event implement the core view without copying or allocation.

### Phase 2: Primitive book

- Implement fixed-capacity best-first arrays with inactive-buffer image validation and swap.
- Validate all delta fields/duplicates before bounded active mutation.
- Track session/sequence evidence and reject stale/reordered updates without inventing exact continuity.
- Implement disconnect/reset/stale invalidation, warm-up, epoch changes, and primitive queries.

### Phase 3: Correctness oracle and replay

- Implement test-only `TreeMap` reference behavior.
- Add example tests for image/delta/delete/query and every fail-closed invariant.
- Add differential jqwik streams and allocation measurement.

### Phase 4: Performance and integration gates

- Add JMH for image, delta, best price, and executable depth.
- Update package/architecture integration and run every default/property/benchmark gate.

## STEP-BY-STEP TASKS

### CREATE `basis-core/.../book` contracts

- **IMPLEMENT**: primitive update view, lifecycle/status enums, trust evidence accessors, and mutable executable result.
- **GOTCHA**: no venue type or collections in production core.
- **VALIDATE**: `./mvnw -o -pl basis-core -am test`
- **SATISFIES**: AC04, AC05, AC17.

### UPDATE `MutableMarketDataEvent`

- **IMPLEMENT**: expose its existing primitive fields through `BookUpdateView` and map event kind to core update type.
- **GOTCHA**: preserve current parser API and replay digests.
- **VALIDATE**: `./mvnw -o -pl basis-venue-bybit,basis-venue-deribit -am test`
- **SATISFIES**: AC04.

### CREATE `FixedDepthOrderBook`

- **IMPLEMENT**: preallocated sorted arrays, double-buffer image commit, bounded delta mutation, sequence/session checks, trust/freshness, primitive queries, checked VWAP/notional arithmetic.
- **GOTCHA**: invalid images never swap; any invalid active mutation revokes trust; no sorting or silent repair.
- **VALIDATE**: focused book unit tests.
- **SATISFIES**: AC04, AC05, AC17.

### CREATE reference and randomized tests

- **IMPLEMENT**: `TreeMap` oracle, deterministic examples, property streams, malformed/crossed/duplicate/capacity/stale/reconnect tests, allocation measurement.
- **VALIDATE**: property profile plus default suite.
- **SATISFIES**: Phase 4 correctness exit gate.

### CREATE JMH benchmarks

- **IMPLEMENT**: representative depth fixtures for image, delta, best price, and executable walk.
- **VALIDATE**: benchmark-profile discovery/smoke.
- **SATISFIES**: Phase 4 performance evidence foundation.

### WRITE implementation report and validate reactor

- **VALIDATE**: all commands below; record deviations and certification gaps.

## TESTING STRATEGY

### Unit Tests

- Ordered image swap; bid/ask insert/update/delete; capacity; queries and rounding.
- Duplicate, invalid tick/lot/size, unordered/crossed image, invalid delta, session reset, reorder, stale deadline, reconnect, and recovery image.

### Property and Differential Tests

- Generate bounded valid images followed by updates and compare every level/query/evidence field after every accepted event.
- Generate malformed variants and assert both models reject with the same reason and the production book is not usable.

### Performance Tests

- Steady-state allocation gate after warm-up.
- JMH mutation/query discovery under the existing benchmark profile; target-host budget certification remains Phase 14 evidence.

## VALIDATION COMMANDS

```bash
./mvnw -o spotless:check
./mvnw -o -T1C clean verify
./mvnw -o -Pproperty-tests -pl basis-core -am verify
./mvnw -o -Pbenchmark-tests -pl basis-core,basis-benchmarks -am test
python3 -m unittest discover -s tools/tests -v
```

## ACCEPTANCE CRITERIA

- [x] Images and deltas produce identical observable state in primitive and reference books.
- [x] Invalid, disconnected, stale, crossed, capacity-exceeded, or reordered state is immediately unusable.
- [x] Recovery requires a valid new image and configured warm-up.
- [x] Production mutation/query paths allocate zero after warm-up.
- [x] JMH discovers representative book mutation and query benchmarks.
- [x] The complete Java 25 reactor and opt-in property/benchmark profiles pass.
