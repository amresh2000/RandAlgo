# Build quality configuration

The root POM owns tool versions and quality gates. Default `verify` is hermetic:
it does not require credentials, network services, databases, or local fixtures.

Test tags are `unit`, `property`, `integration`, `venue-contract`, `replay`,
`chaos`, and `benchmark`. Unit tests are the default; each other category is
enabled by its matching Maven profile. Venue contract tests also require
`-Dvenue.environment=testnet` so they cannot accidentally target production.

Hot-path API restrictions are enforced structurally by the architecture tests
in `basis-app` and unsafe/deprecated JDK calls are rejected globally by the
Forbidden APIs Maven plugin.

Pull requests use GitHub Dependency Review and the scheduled security workflow
uses OSV. A second NVD-backed audit is available through `-Pnvd-audit`; it
requires `NVD_API_KEY` and is not part of hermetic verification.

Phase 12 certification evidence is initialized, hashed, and validated with
`tools/phase12_certification.py`. The evidence file belongs in the approved
external evidence store, never in this directory: it may describe test account
behavior even though it must not contain secrets or account identifiers. See
`docs/phase-12-testnet-certification-runbook.md` for the execution and approval
sequence.
