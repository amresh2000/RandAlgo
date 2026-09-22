# Phase 9 Bybit Private/Order Gateway — Execution Report

## Outcome

Implemented the offline production-shaped Bybit V5 inverse `BTCUSD` private and
order gateway on `feature/phase-9-bybit-private-gateway` in its isolated worktree.
The implementation is fully testable without credentials. Live testnet
certification remains intentionally pending company credentials and authorization.

## Delivered

- Closeable defensive-copy credential owner, HMAC-SHA256 signing, redacted
  rendering, explicit testnet/mainnet endpoints, and exact scaled-decimal encoding.
- Deterministic IOC create, cancel, cancel-all, auth, subscription, and heartbeat
  payloads using the canonical 32-byte local client ID.
- Separate authenticated trade/private session coordinators and strict control
  parsers for their different response envelopes.
- Fixed-capacity request correlation, active-order ownership, rate feedback, and
  execution-identity/content deduplication.
- Authoritative order/execution normalization through a venue-neutral fact
  boundary, including generation fencing, private-disconnect UNKNOWN handling,
  duplicate suppression, and conflict failure.
- Signed bounded HTTP reconciliation for open and history pages, bounded response
  streaming, cursor encoding/pagination, old-generation recovery, duplicate merge,
  and complete-only fact publication.
- Capability contract and credentialed testnet certification runbook.

## Important implementation decisions

- A command success is acceptance only; it never creates a fill or terminal state.
- A submit write advances OEMS to SENT. A cancel write does not replay that
  transition; cancel rejection/uncertainty becomes ambiguous and requires truth.
- Pending command correlations and live orders are separate tables. Private facts
  may arrive before trade responses and safely retire their late correlation.
- Losing the trade socket only ambiguates commands still awaiting a response.
  Losing the private authoritative socket ambiguates every registered nonterminal
  order exactly once.
- Venue adapters do not import OEMS internals. The explicit `basis-venue-api`
  adapter maps neutral facts onto `OrderFactLane`.
- Reconciliation reads realtime/open and history pages before publishing merged
  facts; terminal evidence supersedes stale nonterminal evidence.

## Validation

- Focused Bybit reactor tests: PASS.
- Property-test profile through `basis-venue-bybit`: PASS.
- Architecture and package-classification tests: PASS after introducing the
  neutral venue-order boundary and injected HTTP clock.
- Full Java 25 offline reactor `clean verify`: PASS.
- `git diff --check`: PASS.

## Deliberately pending

- Credentialed testnet executions, account-mode/fee/rate-limit observations, and
  sanitized private-wire captures.
- Testnet proof of cancel-on-disconnect scope; the feature remains disabled.
- Phase 11 application lifecycle/configuration/operator assembly.
- Mainnet authorization and production certification.

## Plan divergence

The original plan expected the Bybit package to publish core facts directly.
The repository architecture test correctly prohibited that dependency, so the
implementation added a small neutral fact API in `basis-venue-api`. Runtime review
also expanded reconciliation from a fakeable seam to a concrete bounded HTTP
transport and added an active-order registry to make private-stream loss safe.
