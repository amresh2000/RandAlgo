# Implementation Report — Phase 5 strategy onboarding and opportunity engine

**Plan**: `.claude/plans/phase-5-opportunity-engine.md`  
**Branch**: `feature/phase-5-opportunity-engine`  
**Status**: COMPLETE

## Summary

Implemented explicit compiled strategy-model registration, timestamped economic inputs, temporal-coherence gating, linear and inverse payoff normalization, and an allocation-free two-direction cross-venue opportunity evaluator. The evaluator walks executable depth, applies conservative fees/carry/conversion/liquidity/latency/reserve economics, and writes a complete reproducible evidence tuple into caller-owned storage.

## Tasks completed

- Checked subtraction and overflow-safe positive 128-bit multiply/divide → `basis-core/.../numeric/CheckedDecimalMath.java` (UPDATE)
- Route identity accessors for opportunity evidence → `basis-core/.../book/FixedDepthOrderBook.java` (UPDATE)
- Compiled payoff, hedge, carry, signal, and execution-policy registry → `basis-strategy-api/.../model` (CREATE)
- Definition-to-registry startup validation → `BasisStrategyDefinitionValidator.java` (UPDATE)
- Immutable economic inputs, risk tables, temporal gate, and opportunity tuple → `basis-strategy-api/.../pricing` (CREATE)
- Linear/inverse payoff, conservative hedge, carry, signal, and policy implementations → `basis-strategy-basis/.../model` (CREATE)
- Complete two-direction executable-depth evaluator → `basis-strategy-basis/.../pricing/CrossVenueBasisStrategy.java` (CREATE)
- Pricing discovery benchmark → `basis-benchmarks/.../CrossVenueBasisStrategyBenchmark.java` (CREATE)

## Tests added

- Registry freeze/duplicate/missing-ID tests and construction of the reference plus two synthetic strategy combinations.
- Temporal age/skew boundaries, stale economics, shallow depth, both directions, result clearing, complete evidence, and linear/inverse end-to-end pricing tests.
- Direct directional carry and liquidity-risk tier/duplicate tests.
- 500 randomized complete-engine BigDecimal comparisons and 1,000 randomized inverse-payoff BigDecimal comparisons.
- A 100,000-iteration steady-state allocation gate after 50,000 warm-up evaluations.

## Validation results

- Java 25 full 12-module clean reactor: PASS.
- Spotless, architecture/package classification, and forbidden APIs: PASS.
- Property profile: PASS, including 1,500 new Phase 5 oracle cases and all existing properties.
- Benchmark profile/allocation gate: PASS.
- JMH benchmark discovery: PASS; an earlier non-forked diagnostic measured approximately 317 ns/op and is not release certification.
- Python tooling tests: PASS, 4 tests.

## Deviations from the plan

- `multiplyDivide` gained an allocation-free 128-bit positive fallback after the inverse BigDecimal oracle exposed intermediate overflow despite an in-range final result. This is the reviewed fixed-width wide-intermediate approach required by the architecture.
- Carry, signal, and execution-policy registrations use concrete compiled model objects instead of ID-presence markers, making startup validation and runtime dispatch fully explicit.
- Each leg has its own conversion rate and generation so cross-currency decisions remain reproducible.

## Issues encountered

- The new inverse end-to-end test initially exposed both a fixture size-bucket mismatch and the real wide-intermediate arithmetic defect. The fixture was corrected to use scale-consistent risk limits, and the arithmetic path received oracle-backed regression coverage.
- Production fee/carry/haircut/latency values and age/skew thresholds remain external certification evidence for Phases 13–14; Phase 5 supplies the fail-closed machinery and does not claim calibrated economics.
