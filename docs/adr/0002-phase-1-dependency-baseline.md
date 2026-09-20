# ADR 0002: Phase 1 dependency and build baseline

- Status: Accepted
- Date: 2026-09-20
- Owners: Platform engineering

## Context

The OMS needs a reproducible Java 25 foundation before safety-critical domain
and venue code is introduced. Floating versions, implicit transitive choices,
or network-dependent default tests would make later correctness and latency
evidence difficult to reproduce.

## Decision

Use Maven Wrapper 3.3.4 with Maven 3.9.16 and require Java 25. The root parent
is the only version authority. Initial runtime-library pins are Aeron 1.53.2,
Agrona 2.6.1, SBE 1.40.2, Netty 4.2.18.Final, and JMH 1.37. Test-library pins
are JUnit 5.14.4, jqwik 1.10.1, and ArchUnit 1.5.0.

Default `verify` remains hermetic. Maven Enforcer checks dependency convergence
and upper bounds, Forbidden APIs rejects unsafe/deprecated JDK calls, Spotless
checks formatting, and ArchUnit enforces module and hot-path boundaries.

Security checks use two independent paths:

- GitHub Dependency Review rejects high-severity additions on pull requests.
- OSV-Scanner 2.6.0 performs the scheduled keyless manifest scan.
- OWASP Dependency-Check 13.0.0 remains available as `-Pnvd-audit` when an
  `NVD_API_KEY` is supplied.

Dependency-Check 13.0.0 cannot perform an unkeyed initial NVD load because of
upstream issue 8715 (fixed after the current release but not yet published to
Maven Central). It therefore cannot be the unattended keyless CI gate.

## Consequences

Child modules declare dependencies but not dependency versions. Any version
change requires updating this ADR or adding a superseding dependency decision,
running convergence plus both vulnerability paths, and repeating the
reproducibility comparison. Networked security data is kept out of the normal
clean-build gate.
