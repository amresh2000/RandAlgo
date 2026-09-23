# Phase 11 application assembly implementation report

## Outcome

Phase 11 is implemented on `feature/phase-11-application-assembly` from the
merged Phase 10 mainline. The process now has an explicit, framework-free
composition boundary; fail-closed startup and arming; authenticated bounded
operator control; signed generation-fenced configuration; core-owned duty-cycle
and health logic; asynchronous metrics; and deadline-bounded ordered shutdown.

## Delivered

- Lifecycle states and startup evidence gate recovery, warm-up, configuration,
  archive, watchdog, and hedge-path health before manual arming.
- HMAC-authenticated operator requests enforce identity, maximum role, expiry,
  command IDs, bounded ingress, fixed result caching, and async responses.
- Critical SBE `OperatorControl` audit facts are admitted before mutations and
  retain operator identity/role, command/action/scope/reason, and both relevant
  generations. Additive protocol action values cover every Phase 11 mutation.
- Signed configuration envelopes validate HMAC and expiry, stage a strictly
  newer generation, and activate only in `DISARMED_READY` while advancing the
  authoritative lifecycle generation.
- The core scheduler samples health first, bounds urgent/high/fair/background
  work, rotates fair/background sources, and records queue-age violations.
- Watchdogs cover progress, queue pressure/age, stale data, time offset, disk,
  venue, and archive health. Fixed histograms feed core-owned hysteretic
  `HedgePathHealth`; cold metrics export cannot alter safety state.
- Startup and shutdown ports express the required order explicitly. Shutdown
  drains/resolves, reconciles, snapshots, flushes, closes private/order paths,
  and closes market-data/infrastructure; failure or timeout enters
  `EMERGENCY_REQUIRED`.
- The operator and emergency shutdown runbook is in
  `docs/phase-11-operator-and-shutdown-runbook.md`.

## Review fixes

The pre-commit review corrected configuration/lifecycle generation divergence,
monotonic-clock wrap in shutdown deadlines, and incomplete identity/generation
fields in durable operator audit facts. The final review has no open findings.

## Validation

All gates passed with Java 25:

- `./mvnw -o spotless:apply`
- `./mvnw -o -pl basis-app -am test`
- `./mvnw -o -Pproperty-tests -pl basis-app -am verify`
- `python3 -m unittest discover -s tools/tests -v` — 4 passed
- `./mvnw -o -T1C clean verify` — all 12 reactor modules passed
- architecture/package classification rules — passed
- `git diff --check` — passed

## Deliberate boundary

This phase supplies the production-shaped control plane and explicit adapter
ports, not live credential injection or testnet order transmission. Wiring the
ports to live Bybit/Deribit sessions and certifying the failure matrix belongs
to Phase 12.
