# Implementation Report — Phase 6 risk, execution groups, and OEMS

**Plan**: `.claude/plans/phase-6-risk-execution.md`  
**Branch**: `feature/phase-6-risk-execution`  
**Base**: Phase 5 commit `3a6a62a`  
**Status**: COMPLETE

## Summary

Implemented the venue-neutral safety and execution core that converts a current Phase 5 opportunity into a generation-fenced worst-case reservation and bounded aggressive IOC execution group. New exposure passes the mandatory ordered risk gate, ambiguous outcomes retain exposure, actual incremental fills create urgent proportional hedges, and failures use separately reserved emergency unwind capacity.

## Tasks completed

- Monotonic hierarchical kills, partitioned rate buckets, and hysteretic hedge-path health → `basis-core/.../risk` (CREATE)
- Generation-fenced risk envelopes, ordered request validation, aggregate strategy ledgers, and reservation lifecycle → `basis-core/.../risk` (CREATE)
- Fixed urgent/normal SPSC command transport with urgent-first draining and observable backpressure → `basis-core/.../command` (CREATE)
- Child-order and execution-group structure-of-arrays tables with bounded indexes, deduplication, UNKNOWN/reconciliation, and terminal reuse → `basis-core/.../oems` (CREATE)
- Reservation-first aggressive IOC coordination, partial-fill proportional hedging, and emergency unwind → `basis-core/.../oems/AggressiveExecutionEngine.java` (CREATE)
- Urgent command round-trip JMH benchmark → `basis-benchmarks/.../PriorityOrderCommandLaneBenchmark.java` (CREATE)

## Tests added

- Kill generation monotonicity, rate partitioning/feedback, and hedge-path recovery hysteresis.
- Exact evidence rejection, ordered kill precedence, aggregate net reservation, unsent token rollback, and narrow emergency risk reduction.
- Urgent-first command draining, bounded backpressure, and a 100,000-iteration allocation gate.
- Child execution idempotency/conflict, ambiguity/reconciliation, cancel/reject transitions, and stale-generation rejection after slot reuse.
- End-to-end initiation, incremental 2:1 hedging, UNKNOWN reservation retention, and correct initiation-leg emergency unwind quantity.
- A 500-case reservation conservation property across randomized gross, collateral, and hedge-claim values.

## Validation results

- Java 25 full 12-module clean reactor: PASS.
- Spotless, architecture/package classification, and forbidden APIs: PASS.
- Focused Phase 6 unit suite: PASS, 41 core tests in the default profile.
- Property profile: PASS, 46 tests including 500 new Phase 6 cases.
- Benchmark profile/allocation gates: PASS, including command publication/drain below one byte per measured iteration.
- JMH benchmark compilation/discovery: PASS.
- Python tooling tests: PASS, 4 tests.

## Deviations from the plan

- Risk and command hot-path benchmarks were represented by the allocation gate plus an urgent command JMH benchmark; end-to-end venue latency remains Phase 7/9/10 work because no private adapter or fake venue exists yet.
- Hedge attempts are bounded by pre-reserved claim count. When a claim or urgent publication is unavailable, the coordinator immediately publishes a separately rate-partitioned unwind instead of adding a configurable pay-up retry ladder.
- The execution engine validates either direct or reversed leg routing against the approved request so both opportunity directions can use the same primitive contract.

## Later-phase boundaries preserved

- Phase 7 supplies deterministic fake-venue scenarios around these state machines.
- Phase 8 persists and recovers reservation/order/group facts.
- Phases 9–10 implement authenticated venue order gateways and reconciliation adapters.
- Phase 11 wires the cell lifecycle and operator control plane.
