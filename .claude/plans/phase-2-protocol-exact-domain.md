# Feature: Phase 2 Protocol and Exact Numeric Domain

This plan implements the binary contracts and exact domain primitives that every later market-data, pricing, risk, execution, journal, and replay phase will share. Validate referenced documentation and dependency behavior before implementation; protocol mistakes become durable compatibility obligations.

## Feature Description

Create a versioned SBE protocol, allocation-conscious exact numeric kernel, immutable instrument and strategy definitions, deterministic clocks/IDs/deadlines, and compatibility/property tests. The phase turns venue metadata and cold configuration into explicit scaled units and stable numeric identifiers without yet connecting to live venue sessions or authorizing a strategy to trade.

## User Story

As an OMS engineer, I want one exact and versioned representation for protocol events, instruments, strategies, units, time, and identifiers so that later components can exchange and replay decisions without ambiguous rounding, overflow, schema drift, or venue-specific leakage.

## Problem Statement

The repository currently has module boundaries but no domain implementation. Phase 3 and later cannot safely parse feeds, maintain books, price opportunities, reserve risk, journal state, or replay events until their units, identifiers, envelope fields, and evolution rules are fixed and executable.

## Solution Statement

Generate flyweight Java codecs from one SBE schema in `basis-protocol`; implement checked primitive arithmetic and immutable definitions in `basis-core`; expose strategy-definition/model contracts through `basis-strategy-api`; and perform strict cold-path configuration parsing/metadata validation in `basis-app`. Back all conversions with independent `BigDecimal` oracles, jqwik properties, checked-in golden SBE frames, and current/previous-version decoding tests.

## Out of Scope / Non-Goals

- No WebSocket/HTTP venue transport, streaming JSON feed parser, or book mutation; those start in Phase 3/4.
- No economically calibrated thresholds or certification of a live reference strategy; Phase 0/13 evidence supplies those values.
- No order state machine, risk reservation, journal, recovery, or live activation.
- No `BigDecimal`, strings, collections, reflection, logging, or allocation-producing APIs in HOT packages.
- No runtime plugin scanning and no generic object envelope.

## Feature Metadata

**Feature Type:** New capability  
**Estimated Complexity:** High  
**Primary Systems Affected:** `basis-protocol`, `basis-core`, `basis-strategy-api`, `basis-app`, `basis-sim`, root Maven build  
**Dependencies:** Java 25, Maven 3.9.16, SBE 1.40.2, Agrona 2.6.1, JUnit 5.14.4, jqwik 1.10.1

## Related Work

**Implements:** `docs/implementation-plan.md` Phase 2  
**Inherits:** `docs/architecture.md`, `docs/component-design.md`, Phase 0 venue/onboarding contracts, ADR-0002 dependency baseline

**Back-references:**

- `.claude/execution-reports/phase-1-engineering-foundation.md` - established the reactor, profiles, architecture gates, and Java 25 baseline.
- `docs/adr/0001-cell-first-v1.md` - freezes the cell-first scope and fail-closed operating model.

**Forward-references:**

- Phase 3 consumes generated protocol IDs/codecs and exact instrument units after Phase 0 wire fixtures are validated.
- Phase 4 consumes numeric primitives, clocks, and deadline facilities for book/trust behavior.

---

## CONTEXT REFERENCES

### Relevant Codebase Files

- `docs/implementation-plan.md:434` - normative Phase 2 tasks and exit gate.
- `docs/component-design.md:47` - primitive memory, named-unit, checked arithmetic, and hot-path bans.
- `docs/component-design.md:128` - immutable catalog construction and metadata comparison requirements.
- `docs/component-design.md:183` - common SBE envelope, sequence domains, and compatibility policy.
- `docs/component-design.md:230` - injected clocks, canonical IDs, and bounded deadline wheel.
- `docs/architecture.md:149` - supported product/payoff/carry universe and metadata drift behavior.
- `docs/architecture.md:174` - complete strategy onboarding definition and lifecycle.
- `docs/strategy-onboarding.md:9` - required definition fields and Phase 0 candidate fixtures.
- `pom.xml:27` - pinned Java/library/plugin properties.
- `pom.xml:65` - central dependency management; child POMs must remain version-free.
- `basis-app/src/test/java/com/penguinsecure/basis/architecture/ArchitectureRulesTest.java` - dependency and HOT-package enforcement pattern.
- `basis-sim/src/test/resources/wire/metadata/` - sanitized Phase 0 venue metadata fixtures for representative inverse, linear, perpetual, and dated products.

### New Files to Create

- `basis-protocol/src/main/resources/sbe/basis-messages.xml` - current canonical SBE schema.
- `basis-protocol/src/test/resources/sbe/v1/` - checked-in golden binary frames plus hashes/manifest.
- `basis-protocol/src/test/java/.../ProtocolCompatibilityTest.java` - current and previous codec compatibility.
- `basis-core/src/main/java/.../numeric/` - scaled parsing, checked scale conversion, rounding, units, and result codes.
- `basis-core/src/main/java/.../product/` - instrument definition, metadata comparison, payoff/carry/conversion primitives.
- `basis-core/src/main/java/.../time/` - injected clock interfaces/adapters and test clocks.
- `basis-core/src/main/java/.../identity/` - dense IDs, 128-bit local order ID, bounded ASCII encoders.
- `basis-core/src/main/java/.../deadline/` - preallocated primitive deadline wheel.
- `basis-strategy-api/src/main/java/.../definition/` - immutable strategy definition, lifecycle/model IDs, validation result.
- `basis-app/src/main/java/.../config/` - strict cold-path parser, canonicalization, SHA-256, and assembly validation.
- `basis-sim/src/test/resources/definitions/` - representative inverse, linear, dated, and explicit-rejection definition fixtures.

Exact filenames inside each package may be split by responsibility, but public types must use named units and stable numeric status/reason codes.

### Relevant Documentation

- [Real Logic SBE 1.40.2 repository documentation](https://github.com/aeron-io/simple-binary-encoding/tree/1.40.2)
  - Use the Java generator/flyweight model; generated codecs are build outputs, not hand-edited source.
- [FIX Simple Binary Encoding specification](https://www.fixtrading.org/standards/sbe-online/)
  - Follow field IDs, block length, schema version, null values, and `sinceVersion` compatibility semantics.
- [Agrona API documentation](https://www.javadoc.io/doc/org.agrona/agrona/2.6.1/)
  - Use `DirectBuffer`/`MutableDirectBuffer`, `NanoClock`, and `EpochNanoClock` contracts at binary/time boundaries.

### Patterns to Follow

**Naming:** `com.penguinsecure.basis.<module>.<concern>`; stable enums/statuses carry explicit numeric codes and reject unknown values.

**Error semantics:** expected parse/validation/conversion outcomes return status plus caller-owned result storage; exceptions indicate programming/configuration invariants on cold paths only.

**Build:** versions stay in the root parent; generated sources are deterministic and attached before compilation; default `verify` stays offline/hermetic after dependency bootstrap.

**Path classification:** every new package gets `package-info.java` with `@path HOT|WARM|COLD` and `@owner`. BigDecimal oracles and parsers are COLD test/support code.

---

## IMPLEMENTATION PLAN

### Phase 1: SBE Protocol Contract and Generation

Define stable numeric enums/composites, the common envelope, stage trace, all Phase 2 messages, and deterministic Maven generation. Keep variable-length fields out of hot messages unless bounded and last.

### Phase 2: Exact Numeric Kernel

**Independent of:** SBE message inventory after shared numeric scales/enums are agreed.

Implement allocation-free decimal byte parsing, checked arithmetic, scale/tick/lot conversions, and explicit rounding policies. Use caller-owned result objects or primitive status returns and test against slow BigDecimal oracles.

### Phase 3: Product and Economic Primitives

**Depends on:** exact numeric kernel.

Implement linear/inverse payoff, canonical exposure, hedge-ratio rounding, funding/carry, expiry/settlement, and currency conversion. Build immutable instrument definitions and exhaustive metadata drift comparison.

### Phase 4: Strategy Definition and Cold Configuration

**Depends on:** product primitives and stable IDs.

Implement immutable `BasisStrategyDefinition`, strict versioned parsing, unknown-field rejection, canonical hashing, leg/model/account compatibility, capacity checks, lifecycle constraints, and explicit unsupported reasons. Phase 0 candidates remain DRAFT/CONTRACT_VALIDATED fixtures rather than economically certified strategies.

### Phase 5: Time, Identity, and Deadline Infrastructure

**Independent of:** product economics after shared primitive status conventions are fixed.

Implement injected epoch/monotonic clocks, restart-fenced IDs, bounded venue encodings, and the fixed-capacity generation-safe deadline wheel.

### Phase 6: Compatibility, Property, and Fixture Certification

**Depends on:** all preceding phases.

Freeze v1 golden frames, verify previous-version decoding, run fuzz/property tests, validate representative Phase 0 metadata/definitions, and prove reproducible generated output.

---

## STEP-BY-STEP TASKS

### 1. UPDATE root and protocol Maven configuration

- **IMPLEMENT:** Add centrally pinned SBE tool/runtime and any required code-generation/build-helper plugin; bind generation before compile in `basis-protocol`; make schema/generated output reproducible.
- **PATTERN:** `pom.xml:27-58` and `pom.xml:177-252`.
- **GOTCHA:** Do not commit generated Java unless a later ADR deliberately chooses that policy; do commit schemas and golden binaries.
- **VALIDATE:** `./mvnw -o -pl basis-protocol -am clean test`
- **SATISFIES:** Phase 2 codec-generation task, AC01.

### 2. CREATE the v1 SBE schema

- **IMPLEMENT:** Common envelope; trace/stage timestamps; instrument definition; book image/delta/trust; opportunity; risk decision; execution group; order command/state; fill; position; health; operator control; snapshot marker; reconciliation result.
- **PATTERN:** `docs/component-design.md:190-224`; preserve venue sequence fields independently.
- **GOTCHA:** Never reuse field/template IDs; append compatible fixed fields with `sinceVersion`; new template/composite for incompatible semantic changes.
- **VALIDATE:** Generate Java, compile, and assert schema IR validation during `basis-protocol` tests.
- **SATISFIES:** Protocol tasks and AC07.

### 3. ADD protocol golden and evolution tests

- **IMPLEMENT:** Encode deterministic v1 fixtures, compare hashes/bytes, decode with current codecs, and include a previous-version schema/codec fixture that current decoders read using acting block length/version.
- **GOTCHA:** A round trip through only the same generated version is insufficient compatibility evidence.
- **VALIDATE:** `./mvnw -o -pl basis-protocol test`
- **SATISFIES:** Phase 2 SBE exit gate.

### 4. CREATE checked decimal and scale primitives

- **IMPLEMENT:** Parse bounded ASCII/UTF-8 byte slices directly to scaled `long`; reject exponent, NaN/infinity, excess fractional precision, sign-only, whitespace, non-ASCII, overflow, and malformed forms. Add exact rescale, multiply/divide, and rounding direction utilities.
- **PATTERN:** `docs/component-design.md:47-66`.
- **GOTCHA:** No intermediate `String`; no saturation; `-0` canonicalizes to zero; risk-increasing rounding is rejected rather than silently adjusted.
- **VALIDATE:** unit tests plus `./mvnw -o -Pproperty-tests -pl basis-core -am test`.
- **SATISFIES:** AC03 and numeric fuzz exit gate.

### 5. CREATE named unit/value contracts

- **IMPLEMENT:** Explicit price ticks, quantity lots/native amount, notional, rate/bps, currency amount, timestamp, and generation contracts with scale metadata and checked conversion boundaries.
- **GOTCHA:** Do not expose unexplained raw `long` values at public component boundaries; avoid wrapper allocation in HOT APIs by pairing semantic method/type names with primitive storage.
- **VALIDATE:** compile-time API review plus exact round-trip and boundary tests.
- **SATISFIES:** AC03 and unit/scale exit gate.

### 6. CREATE payoff, exposure, carry, and conversion models

- **IMPLEMENT:** Certified linear and inverse/reversed payoff formulas; canonical delta/exposure; hedge-ratio conversion with permitted rounding; perpetual funding; dated expiry/settlement; explicit currency conversion and freshness inputs.
- **PATTERN:** `docs/architecture.md:149-172` and Phase 0 instrument contracts.
- **GOTCHA:** Never infer product economics from symbols; every multiplier/contract size/currency comes from `InstrumentDefinition`.
- **VALIDATE:** compare boundary/random cases with independent BigDecimal test oracles and venue examples.
- **SATISFIES:** AC03, AC06 foundation, Phase 0 payoff evidence.

### 7. CREATE immutable instrument definitions and drift comparison

- **IMPLEMENT:** Product family/lifecycle/currencies/scales/tick/lot/min/max/multiplier/expiry/fee-source fields; construction-time invariants; field-specific mismatch reason codes; exact startup comparator.
- **PATTERN:** `docs/component-design.md:128-151`.
- **GOTCHA:** Any active-strategy metadata difference fails closed; no tolerance for lossy decimal conversion.
- **VALIDATE:** parse/compare all sanitized metadata fixtures and deliberately mutate every compared field.
- **SATISFIES:** AC02 and candidate-validation exit gate.

### 8. CREATE immutable strategy-definition contracts and strict parser

- **IMPLEMENT:** Identity/version/lifecycle, two legs, canonical risk, model IDs, economics sources, thresholds, execution policy, feed/age/skew limits, latency budgets, account binding, risk envelope, certification references, effective time, and configuration hash. Reject unknown/missing/duplicate fields and incompatible legs/models.
- **PATTERN:** `docs/strategy-onboarding.md:9-26`.
- **GOTCHA:** The parser is COLD and may allocate; the published runtime definition is immutable/dense. Parsing success does not imply economic certification or activation.
- **VALIDATE:** accepted inverse/linear/dated fixtures plus explicit unsupported/rejection fixtures.
- **SATISFIES:** AC21 foundation and definition exit gate.

### 9. CREATE injected clocks and deterministic test clocks

- **IMPLEMENT:** production adapters for monotonic and epoch nanoseconds plus manually advanced test clocks; prevent direct JVM clock calls outside adapters via architecture tests.
- **PATTERN:** `docs/component-design.md:230-242`.
- **GOTCHA:** elapsed time uses only monotonic time from one process; exchange/epoch time is evidence, never a deadline source.
- **VALIDATE:** unit tests and architecture rule scan.
- **SATISFIES:** AC27 foundation.

### 10. CREATE canonical local IDs and venue encoders

- **IMPLEMENT:** 128-bit primitive-pair local order ID with cell, venue, session generation, strategy slot, and sequence; reusable bounded ASCII encoding; overflow/length/truncation checks.
- **PATTERN:** `docs/component-design.md:244-252`.
- **GOTCHA:** no reuse across session generations and no heap strings in hot encoding.
- **VALIDATE:** uniqueness, restart fencing, maximum length, and deterministic round-trip/property tests.
- **SATISFIES:** execution identity foundation for AC10/AC11.

### 11. CREATE preallocated primitive deadline wheel

- **IMPLEMENT:** fixed slots, generations, deadline/type/owner primitive arrays, explicit capacity failure, cancellation, bounded expiry per duty cycle, and wraparound-safe comparisons.
- **PATTERN:** `docs/component-design.md:254-259`.
- **GOTCHA:** stale handles must not cancel reused slots; a timer storm must not create an unbounded scan or allocation.
- **VALIDATE:** deterministic clock tests for wrap, cancellation, generation reuse, capacity, ordering, and bounded draining.
- **SATISFIES:** timing foundation for AC09/AC24/AC29.

### 12. EXTEND architecture and classification tests

- **IMPLEMENT:** Package annotations for every new package; prohibit BigDecimal/config parser dependencies in HOT packages; enforce clock-adapter-only JVM clock access and protocol/core dependency direction.
- **PATTERN:** existing `ArchitectureRulesTest` and `PackageClassificationTest`.
- **VALIDATE:** `./mvnw -o -pl basis-app -am test`
- **SATISFIES:** AC26.

### 13. RUN the complete Phase 2 gate

- **IMPLEMENT:** Full build, property profiles, reproducibility comparison, golden compatibility, metadata/strategy fixture matrix, and dependency audit.
- **VALIDATE:** `./mvnw -o -T1C clean verify`; `./mvnw -o -Pproperty-tests test`; two clean builds with identical generated-code and JAR hashes.
- **SATISFIES:** all Phase 2 exit criteria.

---

## TESTING STRATEGY

### Unit Tests

- Every parser/status branch, numeric boundary, rounding mode, model formula, metadata field mismatch, lifecycle rule, ID bound, and deadline-wheel transition.
- Deterministic fixtures only; no network, secrets, current wall clock, locale, or timezone dependence.

### Property Tests

- Byte-parser results equal BigDecimal oracle when representable and reject otherwise.
- Conversion/payoff operations either equal the oracle exactly under policy or report overflow/unsupported rounding.
- Encode/decode preserves every supported protocol field and stable enum code.
- IDs remain unique across slot/sequence/session ranges; deadline handles cannot affect a later generation.

### Compatibility and Fixtures

- Current codecs decode current and v1 golden frames.
- Golden bytes/hashes change only with deliberate schema-version updates.
- All Phase 0 metadata candidates produce either a valid definition or an exact unsupported reason.

### Edge Cases

- `Long.MIN_VALUE` sign handling, leading plus/minus, zero and negative zero, maximum precision, scale loss, multiply/divide overflow, zero divisors, expiry boundaries, stale conversion inputs, mismatched currencies/expiry, unknown enum codes, acting-version omissions, deadline tick wrap, and full-capacity wheels.

---

## VALIDATION COMMANDS

### Level 1: Syntax and Style

```bash
./mvnw -o -T1C spotless:check
git diff --check
```

### Level 2: Unit and Architecture Tests

```bash
./mvnw -o -pl basis-protocol,basis-core,basis-strategy-api,basis-app -am test
```

### Level 3: Property and Compatibility Tests

```bash
./mvnw -o -Pproperty-tests test
```

### Level 4: Full Reactor Gate

```bash
./mvnw -o -T1C clean verify
python3 -m unittest discover -s tools/tests -v
```

### Level 5: Reproducibility

Run two clean offline builds and compare SHA-256 for generated sources, golden artifacts, and every JAR.

---

## ACCEPTANCE CRITERIA

- [ ] Every protocol event has a stable template/field contract and common evidence envelope.
- [ ] Generated codecs are deterministic and current decoders read current plus previous-version golden frames.
- [ ] Every admitted venue/product value has an explicit unit/scale and exact round-trip coverage.
- [ ] Decimal parsing performs no intermediate string creation and never silently loses scale or overflows.
- [ ] Linear, inverse, carry, settlement, and conversion results match independent BigDecimal oracles.
- [ ] Metadata drift blocks activation with a precise reason.
- [ ] Inverse, linear, and dated candidate definitions pass or fail with explicit unsupported reasons.
- [ ] Strategy parsing rejects unknown, duplicate, missing, ambiguous, or incompatible fields.
- [ ] Clocks are injected, IDs are restart-fenced/bounded, and the deadline wheel is fixed-capacity/generation-safe.
- [ ] Default clean verification remains hermetic and all Phase 1 gates continue to pass.

## COMPLETION CHECKLIST

- [ ] Tasks 1-13 completed in order, with independent tasks interleaved only after shared contracts are fixed.
- [ ] Each task's focused validation passed immediately.
- [ ] Full unit, property, architecture, compatibility, and fixture suites pass.
- [ ] Generated output and JARs are reproducible.
- [ ] Technical review and execution report completed before commit.
- [ ] Phase 0-dependent values remain evidence placeholders, not fabricated certifications.

## OPEN QUESTIONS / ASSUMPTIONS

- Assumption: use a strict versioned JSON definition format in the COLD `basis-app` configuration package. If another signed configuration format is desired, decide before Task 8; domain types and validation remain unchanged.
- Assumption: SBE-generated Java remains build output, while schemas and binary golden frames are committed.
- Assumption: protocol schema v1 may be frozen now even though later phases will append compatible fields; incompatible semantics receive new template IDs.
- Phase 0 data is not required to implement generic exact models. It is required before marking a candidate economically certified and before Phase 3 trusts venue wire semantics.

## NOTES

Recommended sequencing is protocol vocabulary first, then numeric kernel, then product/definition validation, with time/ID/deadline work proceeding independently after shared status conventions are frozen. This delivers most of Phase 2 without pretending the pending seven-day dataset has selected a production strategy.

## AMENDMENTS

