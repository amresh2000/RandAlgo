# Implementation Report — Phase 4 primitive books and trust lifecycle

**Plan**: `.claude/plans/phase-4-primitive-books.md`  
**Branch**: `feature/phase-4-primitive-books`  
**Status**: COMPLETE

## Summary

Implemented a fixed-capacity, single-writer primitive order book with atomic double-buffer mutation, native sequence/session evidence, explicit trust epochs and failure reasons, freshness enforcement, and caller-owned executable-depth results. The Phase 3 normalized event now implements a core-owned primitive view without introducing a core-to-venue dependency. Correctness is checked against a test-only `TreeMap` oracle and allocation/performance gates.

## Tasks completed

- Core book/update/trust/query contracts → `basis-core/.../book` (CREATE)
- Phase 3 event integration → `MutableMarketDataEvent` (UPDATE)
- Primitive image/delta mutation and trust lifecycle → `FixedDepthOrderBook` (CREATE)
- Reference/differential/unit/allocation coverage → `basis-core/src/test/.../book` (CREATE)
- Image, delta, best-price, and executable-depth JMH fixtures → `FixedDepthOrderBookBenchmark` (CREATE)

## Tests added

- Six focused unit tests cover image/delta mutation, ordering, insertion/update/delete, executable pricing, warm-up, stale/session recovery, invalid grids, duplicates, crossing, empty/one-sided state, and epoch behavior.
- Two jqwik properties run 1,500 generated cases for primitive/reference agreement and crossed-image rejection.
- One benchmark-profile allocation test measures 100,000 mutation/query iterations after 50,000 warm-up iterations with a sub-byte-per-iteration ceiling.
- Four JMH benchmarks are generated and a non-forked discovery smoke completes.

## Validation results

- Focused Java 25 compilation/unit suite: PASS.
- Property profile: PASS, including 1,500 new generated cases.
- Benchmark profile/allocation gate: PASS.
- JMH generation and non-forked smoke: PASS. Forked local smoke cannot bind JMH's control socket in the sandbox and is not treated as performance certification.
- Full 12-module Java 25 clean reactor: PASS.
- Spotless, architecture/package classification, and forbidden APIs: PASS.
- Python Phase 0 tooling: PASS, 4 tests.

## Deviations from the plan

- Delta application also uses the inactive preallocated buffer and swaps only after final validation. This is stronger atomicity than mutating the active arrays in place and uses no additional runtime allocation.
- Laptop JMH numbers are diagnostic only. The normative forked/captured-distribution comparison against the reference model remains target-host evidence work.

## Issues encountered

- The Context7 CLI requires a newer Node runtime than the installed Node 18; official jqwik documentation and the repository's pinned integration were used instead.
- Forked JMH requires a local control socket, which the sandbox blocks. Non-forked smoke verified benchmark execution.
