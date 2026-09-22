# Feature: Phase 5 strategy onboarding and opportunity engine

This plan implements `docs/implementation-plan.md` Phase 5 on top of the Phase 4 primitive books. Production fee schedules, carry values, haircut/latency tables, and temporal thresholds remain external certified evidence; this phase builds the fail-closed machinery that consumes them.

## Feature Description

Build explicit compiled-model registration, fixed-point payoff/hedge conversion, versioned economic inputs, temporal-coherence validation, and a reusable two-direction cross-venue basis engine. The engine walks executable book depth, applies every configured cost with conservative rounding, and writes a complete evidence tuple into a caller-owned opportunity result.

## User Story

As the single-writer execution core, I want to evaluate both directions of a configured basis strategy from trusted executable depth and current economic evidence so that downstream risk can authorize only reproducible, temporally coherent, net-positive opportunities.

## Problem Statement

Phase 4 exposes trusted primitive books but there is no compiled model registry, no fail-closed fee/carry/risk-input contract, no cross-leg age/skew gate, and no economic decision object. Midpoint or gross-spread calculations would ignore depth, product payoff, costs, stale evidence, and asymmetric feed arrival.

## Solution Statement

Add hot-path strategy API contracts for payoff and hedge models, a bounded explicit registry, immutable timestamped economic inputs, a fixed table keyed by direction/venue pair/size/volatility, and caller-owned opportunity results. Implement certified linear and inverse payoff models plus a reusable basis engine in `basis-strategy-basis`. Use checked scaled-long arithmetic throughout and BigDecimal only in test oracles.

## Out of Scope / Non-Goals

- No risk reservation, execution groups, order submission, or hedging; those begin in Phase 6.
- No production claims for fee, funding, latency, haircut, or age/skew thresholds; Phases 13–14 calibrate them.
- No classpath scanning, plugins, dynamic bytecode, floating point, runtime collections, or steady-state allocation.
- No passive-maker economics or portfolio netting.

## Feature Metadata

**Feature Type**: New capability  
**Estimated Complexity**: High  
**Primary Systems Affected**: `basis-core`, `basis-strategy-api`, `basis-strategy-basis`, `basis-app`, `basis-benchmarks`  
**Dependencies**: Java 25, JUnit 5.14.4, jqwik 1.10.1, JMH 1.37; no new library

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 5 / atomic tasks 9–10  
**Epic**: `docs/implementation-plan.md`

**Back-references**:

- `.claude/plans/phase-4-primitive-books.md` - trusted primitive depth and evidence source.
- `docs/architecture.md` sections 5 and 8 - product normalization, opportunity contents, and fixed-point economics.
- `docs/component-design.md` sections 3, 10, and 11 - explicit registration, pricing order, temporal gate, and strategy runtime boundary.

**Forward-references**:

- Phase 6 will revalidate the evidence tuple and reserve risk before execution.

---

## CONTEXT REFERENCES

### Relevant Codebase Files

- `basis-core/src/main/java/com/penguinsecure/basis/core/book/FixedDepthOrderBook.java:169` - trust/freshness and executable-depth API.
- `basis-core/src/main/java/com/penguinsecure/basis/core/product/PayoffMath.java:12` - checked linear, inverse, rate, conversion, and expiry primitives.
- `basis-core/src/main/java/com/penguinsecure/basis/core/numeric/CheckedDecimalMath.java:36` - mandatory checked arithmetic and rounding.
- `basis-core/src/main/java/com/penguinsecure/basis/core/product/InstrumentDefinition.java:6` - runtime units, multiplier, and lifecycle.
- `basis-strategy-api/src/main/java/com/penguinsecure/basis/strategy/api/definition/BasisStrategyDefinition.java:6` - versioned strategy, model/source IDs, temporal limits, and risk maxima.
- `basis-strategy-api/src/main/java/com/penguinsecure/basis/strategy/api/definition/BasisStrategyDefinitionValidator.java:6` - structural onboarding gate to extend with registry validation.
- `basis-app/src/test/resources/definitions/*.json` - reference inverse perpetual plus linear and dated synthetic definitions.
- `basis-app/src/test/java/com/penguinsecure/basis/architecture/ArchitectureRulesTest.java:84` - hot-path API restrictions.

### New Files to Create

- `basis-strategy-api/.../model/*` - payoff/hedge interfaces and bounded explicit registry.
- `basis-strategy-api/.../pricing/*` - direction/status, temporal gate, input metadata, fee/rate/table inputs, and mutable opportunity.
- `basis-strategy-basis/.../model/*` - certified linear/inverse payoff and common hedge-ratio implementations.
- `basis-strategy-basis/.../pricing/CrossVenueBasisStrategy.java` - reusable two-direction evaluator.
- Unit/property/oracle/allocation tests in the corresponding test packages.
- `basis-benchmarks/.../CrossVenueBasisStrategyBenchmark.java` - hot evaluation benchmark.

### Relevant Documentation

- `docs/component-design.md#10-pricing-and-economic-inputs` - normative pricing sequence and evidence fields.
- `docs/performance/latency-contract.md` - same-process monotonic age/skew contract.
- `docs/economic-gates/btcusd-btc-perpetual.md` - predeclared complete-cost calculation and evidence caveat.
- `docs/strategy-onboarding.md` - definition-only versus compiled-model onboarding tiers.

### Patterns to Follow

- Final production classes, primitive IDs, fixed arrays, caller-owned mutable result slots, stable numeric status codes.
- Expected rejection is returned, not thrown; constructors reject programmer/configuration errors.
- `@path HOT` package classification and no clocks, BigDecimal, collections, streams, reflection, or logging in hot code.
- All economic sources carry identity, generation, effective/receive/expiry time, provenance, and confidence; missing/mismatched/expired evidence rejects pricing.

---

## IMPLEMENTATION PLAN

### Phase 1: Model and input contracts

- Define payoff/hedge contracts and a fixed-capacity explicit registry with duplicate/frozen/type checks.
- Extend definition validation with registry-aware model-ID resolution.
- Define immutable fee, carry/conversion/risk-rate inputs and a fixed lookup table with full metadata.

### Phase 2: Temporal and opportunity contracts

- Implement the same-clock per-leg age/skew gate with a distinct incoherence status that never mutates otherwise trusted books.
- Implement caller-owned opportunity storage for direction, quantities/prices, complete cost decomposition, every book/input/config generation, ages/skew, decision time, maximum exposure, and expiry.

### Phase 3: Certified models and reusable evaluator

- Implement exact linear and inverse payoff models and a conservative hedge-ratio model.
- Implement both evaluation directions: haircut capacity, requested canonical size conversion, opposite-side VWAPs, normalization, fees, funding/settlement/conversion, slippage, latency risk, reserve, net edge, and threshold.
- Fail closed on untrusted books, partial depth, overflow, stale/mismatched inputs, or unsupported units.

### Phase 4: Certification tests and performance evidence

- Add registry, temporal-boundary, economic expiry/provenance, two-direction, cost-decomposition, evidence-reproduction, onboarding, and allocation tests.
- Add jqwik/BigDecimal differential tests around break-even and conservative rounding.
- Add JMH discovery for opportunity evaluation and run the full reactor gates.

## STEP-BY-STEP TASKS

### CREATE strategy model contracts and explicit registry

- **IMPLEMENT**: fixed model ID arrays, registration before freeze, registry-aware definition validation, no reflection.
- **VALIDATE**: `./mvnw -o -pl basis-strategy-api -am test`
- **SATISFIES**: Phase 5 registration and AC21 onboarding.

### CREATE economic input and temporal contracts

- **IMPLEMENT**: metadata, fee schedules, carry/conversion rates, fixed keyed risk table, source/generation/freshness checks, temporal gate.
- **GOTCHA**: no missing cost defaults to zero; skew failure leaves both books trusted.
- **VALIDATE**: focused strategy API unit tests.
- **SATISFIES**: AC06, AC07, AC27.

### CREATE payoff models and `CrossVenueBasisStrategy`

- **IMPLEMENT**: exact linear/inverse exposure and cash-flow conversion, native quantity matching, maximum executable canonical exposure, two-direction complete-cost evaluation, full evidence stamp.
- **GOTCHA**: adverse rounding for costs and exposure; reject partial depth and arithmetic overflow.
- **VALIDATE**: focused strategy implementation tests.
- **SATISFIES**: AC06, AC07, AC21.

### CREATE oracle, property, onboarding, and allocation tests

- **IMPLEMENT**: BigDecimal reference, randomized break-even cases, age/skew boundaries, stale inputs, reference plus two synthetic definitions, zero-allocation steady state.
- **VALIDATE**: default/property/benchmark profiles.
- **SATISFIES**: Phase 5 exit gate.

### CREATE benchmark, report, review, and reactor validation

- **IMPLEMENT**: reusable prebuilt fixture and caller-owned result benchmark; document certification limitations.
- **VALIDATE**: all commands below.

## TESTING STRATEGY

### Unit Tests

- Registry duplicate/capacity/freeze/missing-ID cases and all three onboarding definitions.
- Temporal age at/beyond limits, clock reversal, skew at/beyond limit, and trusted-book preservation.
- Fee maker/taker choice; source/generation/effective/expiry/confidence validation; risk-table key and evidence age.
- Linear/inverse conversion, both trade directions, insufficient depth, stale books, stale inputs, overflow, complete evidence and cost reproduction.

### Property and Differential Tests

- Generate safe prices, quantities, fee/carry/risk rates, and thresholds around break-even.
- Compare fixed-point components and net edge with a BigDecimal oracle under the declared adverse rounding bound.

### Performance Tests

- Measure allocations after warm-up and require sub-byte-per-evaluation noise tolerance.
- Add JMH for both-direction evaluation; target-host latency certification remains a later evidence gate.

## VALIDATION COMMANDS

```bash
./mvnw -o spotless:check
./mvnw -o -T1C clean verify
./mvnw -o -Pproperty-tests -pl basis-strategy-basis -am verify
./mvnw -o -Pbenchmark-tests -pl basis-strategy-basis,basis-benchmarks -am test
python3 -m unittest discover -s tools/tests -v
```

## ACCEPTANCE CRITERIA

- [ ] Every definition model ID resolves through an explicit frozen registry.
- [ ] No opportunity is emitted from untrusted/stale books, incoherent receive times, partial depth, or invalid/expired economic evidence.
- [ ] Both directions use executable opposite-side depth and emit a complete fixed-point cost/evidence decomposition.
- [ ] Linear, inverse, perpetual, and dated configurations onboard without changes below the model/strategy layer.
- [ ] Fixed-point and BigDecimal results agree within the documented conservative rounding bound.
- [ ] Steady-state evaluation is allocation-free and all Java 25 reactor/profile gates pass.
