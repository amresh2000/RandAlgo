# Basis OMS

Greenfield Java trading platform for onboarding and operating low-latency
cross-venue basis strategies between Bybit and Deribit.

The repository is currently in the architecture and implementation-planning
stage. No production implementation is implied by the presence of these
documents.

## Golden sources

- [Architecture](docs/architecture.md) - accepted system boundaries and design decisions.
- [Component design](docs/component-design.md) - normative per-component contracts, data structures, ownership, failure behavior, and low-latency choices.
- [Implementation plan](docs/implementation-plan.md) - phase order, acceptance gates, tasks, and validation.
- [Advanced execution design](docs/advanced-execution-design.md) - post-v1 passive-maker and venue-proximate-cell contracts and promotion gates.

If implementation experience conflicts with the architecture, record the
change as an ADR before changing the implementation. Venue documentation and
observed wire behavior override assumptions in these documents.
