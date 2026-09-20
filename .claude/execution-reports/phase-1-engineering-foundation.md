# Phase 1 engineering foundation execution report

## Meta information

- Plan file: `docs/implementation-plan.md`, Phase 1
- Branch: `feature/phase-1-engineering-foundation`
- Worktree: `/Users/local.admin/CODE/ALGO/basis-oms/.worktrees/phase-1-engineering-foundation`
- Files added: 41
- Files modified: 3
- Files deleted: 0
- Lines changed: +1990 / -3
- Main surfaces:
  - Root Maven parent/BOM and 11 child modules
  - Maven Wrapper 3.3.4 pinned to Maven 3.9.16 with SHA-256 verification
  - Java 25 toolchain/compiler policy and reproducible-build settings
  - Package classifications and ArchUnit rules
  - Unit/property/integration/venue-contract/replay/chaos/benchmark wiring
  - Linux CI, dependency audit, and dedicated performance workflows
  - Dependency baseline ADR and build documentation

## Validation results

- Syntax and linting: PASS — Spotless, POM sorting, Forbidden APIs, YAML parse,
  and `git diff --check` pass.
- Type checking/compilation: PASS — all 12 reactor projects compile with
  `--release 25` and warnings treated as errors.
- Unit tests: PASS — 6 architecture/classification tests and 4 retained Phase 0
  Python tests.
- Property tests: PASS — the jqwik sentinel is discovered only by
  `-Pproperty-tests`.
- Integration profiles: PASS — integration, replay, chaos, and venue-contract
  profiles each discover only their tagged Failsafe sentinel.
- Safety guard: PASS — venue-contract validation refuses to start without
  `-Dvenue.environment=testnet`.
- Hermetic gate: PASS — `./mvnw -o -T1C clean verify` succeeds after bootstrap.
- Reproducibility: PASS — two clean builds produce identical SHA-256 values for
  all 12 JAR outputs.
- Dependency convergence: PASS — Maven Enforcer convergence and upper-bound
  rules pass across all modules.
- Vulnerability evidence: PASS — OSV-Scanner 2.6.0 found no known issues in the
  pinned third-party manifests; GitHub Dependency Review and scheduled OSV CI
  gates are configured.
- Benchmark harness: PASS — JMH 1.37 discovers, forks, and executes the smoke
  benchmark under Java 25.

## What went well

- The documented component map translated cleanly into an acyclic reactor.
- Central dependency management kept every child dependency version-free.
- Package metadata drives the hot-path rules, so future HOT packages inherit
  restrictions without editing a hard-coded package list.
- Offline verification and byte-for-byte rebuild checks both passed early.

## Challenges encountered

- Context7 requires a newer Node runtime than the installed Node 18, so current
  Maven/plugin behavior was verified through official project documentation and
  Maven Central metadata instead.
- OWASP Dependency-Check 13.0.0 has an upstream regression that sends an empty
  NVD key during an unkeyed initial load. The scan aborts before analysis.
- JMH needs a local fork-control socket; the sandbox blocked it, and the same
  command passed when run with the required local permission.
- The first category design configured Failsafe without lifecycle bindings; the
  pre-commit review found and corrected this before commit.

## Divergences from plan

### Keyless vulnerability gate

- Planned: Run a vulnerability check alongside convergence checks.
- Actual: Scheduled CI uses OSV-Scanner 2.6.0 and pull requests use GitHub
  Dependency Review; OWASP Dependency-Check remains a keyed `-Pnvd-audit`.
- Reason: Dependency-Check 13.0.0 upstream issue 8715 prevents an unkeyed first
  database load. OSV supplied current evidence without weakening the gate.
- Type: Better approach found / plan assumption wrong.

### Phase 0 dependency

- Planned: Phase 1 depends on Phase 0 scope acceptance.
- Actual: The engineering foundation was implemented on top of the Phase 0
  evidence commit while the seven-day economic and entitlement work remains
  open.
- Reason: The user explicitly authorized Phase 1; its reversible build work can
  proceed independently. Phase 2 remains blocked until Phase 0 exits.
- Type: Other (sequencing decision).

## Skipped or externally pending

- The clean Linux and dedicated performance workflows cannot execute locally;
  their first authoritative runs occur after the branch is pushed.
- The NVD-backed audit was not completed because no `NVD_API_KEY` was supplied;
  the independent OSV audit completed successfully.
- No production domain, protocol schema, venue, or trading behavior was added;
  those belong to Phase 2 and later.

## Recommendations

- Keep Java 25 selected explicitly until the workstation default is upgraded;
  the Homebrew JDK was deliberately left keg-only.
- Configure an NVD API key before using `-Pnvd-audit` in an unattended job.
- Do not start Phase 2 until the remaining Phase 0 scope/economic acceptance is
  explicitly closed.
- Treat any dependency version change as an ADR update plus convergence,
  vulnerability, reproducibility, and benchmark-harness reruns.
