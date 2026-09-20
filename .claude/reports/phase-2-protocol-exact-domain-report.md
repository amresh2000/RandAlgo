# Phase 2 Protocol and Exact Domain Implementation Report

## Outcome

Phase 2 establishes the versioned binary and exact numeric/domain contracts required by subsequent market-data, pricing, risk, execution, journaling, and replay phases. It does not connect to live venue sessions or authorize trading.

## Implemented

- Added deterministic SBE codec generation and a schema containing the common evidence envelope, stage trace, stable enums, and 15 required message templates.
- Added checked-in v0/v1 instrument-definition golden frames, current encoding checks, and current-decoder/previous-version compatibility checks.
- Added direct byte-to-scaled-long decimal parsing, checked add/multiply/divide/rescale/multiply-divide operations, explicit rounding policies, named units, and scale contracts.
- Added immutable instrument definitions, exact fail-closed metadata comparison, product-family/lifecycle contracts, linear and inverse exposure/payoff operations, rate/carry arithmetic, conversion, and expiry evaluation.
- Added immutable strategy/leg/model/economic-source/risk definitions, structural validation, strict streaming JSON parsing, canonical SHA-256-derived identity, and representative inverse, linear, dated, and rejected fixtures.
- Added injected monotonic/epoch clocks and isolated production JVM clock adapters.
- Added restart-fenced 128-bit local order IDs and fixed-width allocation-free venue encoding/decoding.
- Added a fixed-capacity primitive deadline scheduler with generation-safe handles, O(1) free-slot acquisition, cancellation, bounded expiry work, and signed wrap-safe monotonic comparisons.
- Extended architecture tests to keep JVM clock reads inside adapters and preserve HOT-package and module-boundary rules.

## Plan Divergences

- SBE generation uses the official `sbe-tool` main class through `exec-maven-plugin`; SBE 1.40.2 does not publish a dedicated Maven generator plugin.
- Representative strategy fixtures live in `basis-app` test resources because the strict parser and its test boundary are owned by that module. Phase 0 venue wire metadata remains in `basis-sim` for later venue-specific catalog assembly.
- The deadline implementation is a fixed-capacity rotating primitive slot wheel rather than a time-bucket hierarchy. Its duty-cycle work and expirations are caller-bounded, which is the Phase 2 behavioral requirement; benchmark-driven bucketing can be introduced without changing its handle contract.

## Validation

- `./mvnw -o -pl basis-protocol -am clean test`
- `./mvnw -o -pl basis-app -am clean test`
- `./mvnw -o -T1C clean verify`
- `./mvnw -o -Pproperty-tests test` (2,000 decimal-parser and 1,000 payoff oracle trials)
- Two independent clean packages produced identical aggregate generated-source and JAR SHA-256 hashes.
- Pre-commit technical review completed; all findings were fixed and regression-tested.

## Remaining Scope

Live venue metadata parsing/catalog assembly, transport/feed handling, book mutation, risk reservation, order state, and production activation remain intentionally assigned to later phases.
