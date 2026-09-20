# ADR-0001: Single Cell for Version One

- Status: Accepted for Phase 0
- Date: 2026-09-20
- Owners: Basis OMS engineering and risk

## Context

The first release must validate a reusable Bybit/Deribit basis strategy while
remaining safe under stale books, partial fills, ambiguous order outcomes, and
restart. Splitting market data, strategy, risk, and order management into
services would add distributed ownership and recovery failure modes before the
economic case is proven.

## Decision

Version one will run as one execution-cell JVM on one host. Venue network agents
own their connections and communicate with a single state-owning core thread
through bounded SPSC lanes. Books, pricing, strategy, risk, and OEMS execute by
direct calls on that core thread. Journal, telemetry, configuration, and
operator work remain asynchronous.

The initial execution policy is aggressive initiation followed by an urgent,
fill-driven aggressive hedge. Passive maker initiation and regional cells are
not authorized by this ADR.

## Consequences

- One thread owns all mutable trading state and deterministic replay ordering.
- The hot path has no database, blocking I/O, textual logging, or remote RPC.
- Every boundary retains versioned binary semantics suitable for a later split.
- The single host cannot be venue-local to both exchanges; Phase 0 measures the
  trade-off and Phase 13 selects the production location from economic evidence.
- Regional cells require a later ADR, idempotent hedge protocol, partition
  certification, and evidence that their benefit exceeds distributed-state risk.

## Validation

This ADR is accepted only with the Phase 0 venue contracts, onboarding
contract, conservative economic gate, latency contract, and candidate-region
report. Implementation deviations require a superseding ADR.

