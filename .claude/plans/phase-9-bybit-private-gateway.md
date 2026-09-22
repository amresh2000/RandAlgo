# Feature: Phase 9 Bybit private stream and order gateway

This plan implements `docs/implementation-plan.md` Phase 9 on top of the Phase 8 recovery boundary and the credential-free public-feed runner. It inherits the socket separation, bounded single-owner agents, authoritative-private-fact, UNKNOWN, reconciliation, and secret-isolation decisions in `docs/architecture.md` and `docs/component-design.md`.

## Feature Description

Build the Bybit V5 inverse-product private/order adapter. Separate authenticated trade and private-stream sessions accept bounded commands, generate deterministic `reqId`/`orderLinkId` values, serialize exact native amounts, normalize asynchronous command responses and authoritative order/execution updates into `MutableOrderFact`, deduplicate executions, track rate feedback, and reconcile unresolved orders through bounded HTTP pages. No command acknowledgement is treated as a fill or terminal venue truth.

## User Story

As the execution-cell owner, I want every Bybit command and private execution to resolve through authoritative, generation-fenced facts so that disconnects, lost acknowledgements, duplicates, and cancel races cannot create blind retransmission or hidden exposure.

## Problem Statement

The repository currently has only Bybit public market data. Core command/fact lanes, UNKNOWN handling, deterministic client IDs, simulation faults, journal facts, and recovery ports exist, but there is no Bybit authentication, private/trade protocol codec, request correlation, execution dedupe, rate feedback, or reconciliation implementation.

## Solution Statement

Create a `com.penguinsecure.basis.venue.bybit.order` adapter package. Secrets remain inside a closeable credential/signing object and are never exposed by accessors, logs, exceptions, commands, or facts. A bounded order profile maps internal instrument IDs to certified Bybit category/symbol/scales. A deterministic codec converts commands to V5 JSON without floating point. Trade-response parsing emits write acceptance/failure/rate facts only; private order/execution parsing emits authoritative lifecycle/fill facts. Fixed-capacity correlation and execution-identity tables fence sessions and verify duplicate content. A bounded reconciliation coordinator consumes paginated transport results and emits `RECONCILED` facts without scanning unbounded history.

## Recommended Direction

Implement and certify the complete offline protocol boundary now. Keep actual credential injection and testnet execution out of source and disabled until the company provides a withdrawal-disabled testnet subaccount and explicitly authorizes live order tests. This preserves progress without weakening the Phase 9 exit rule.

## Out of Scope / Non-Goals

- No real-money/mainnet order transmission.
- No credentials, environment parsing, or secret files in the repository.
- No Deribit private/order work; Phase 10 owns it.
- No Phase 11 application lifecycle/operator endpoint assembly.
- No claim that offline fixtures complete testnet certification.
- No use of REST as the normal order path.

## Feature Metadata

**Feature Type**: New capability / safety-critical venue adapter  
**Estimated Complexity**: Very high  
**Primary Systems Affected**: `basis-venue-bybit`, existing core command/fact and venue session APIs, documentation  
**Dependencies**: Java 25, Netty 4.2, Bybit V5 trade/private/REST contracts, Phases 6-8

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 9 / task 14  
**Back-references**: `.claude/plans/phase-6-risk-execution.md`, `.claude/plans/phase-7-deterministic-simulator.md`, `.claude/plans/phase-8-journal-snapshot-recovery.md`  
**Forward-references**: Phase 11 application assembly and Phase 12 credentialed testnet certification.

---

## CONTEXT REFERENCES

### Relevant Codebase Files

- `docs/component-design.md:640` — normative venue-gateway ownership and Bybit semantics.
- `docs/architecture.md:390` — child-order UNKNOWN and reconciliation invariants.
- `basis-core/src/main/java/com/penguinsecure/basis/core/command/MutableOrderCommand.java:1` — outbound reusable command view.
- `basis-core/src/main/java/com/penguinsecure/basis/core/identity/VenueClientIdEncoder.java:1` — exact 32-byte client ID.
- `basis-core/src/main/java/com/penguinsecure/basis/core/oems/fact/MutableOrderFact.java:1` — normalized fact target.
- `basis-venue-api/src/main/java/com/penguinsecure/basis/venue/api/lane/OrderFactLane.java:1` — bounded critical ingress.
- `basis-venue-api/src/main/java/com/penguinsecure/basis/venue/api/session/SessionStateMachine.java:1` — legal session transitions/generation fencing.
- `basis-venue-bybit/src/main/java/com/penguinsecure/basis/venue/bybit/marketdata/BybitNettyWebSocketConnection.java:25` — bounded TLS/WebSocket construction pattern.
- `basis-venue-bybit/src/main/java/com/penguinsecure/basis/venue/bybit/marketdata/BybitOrderBookParser.java:13` — bounded cursor parser pattern.
- `basis-sim/src/main/java/com/penguinsecure/basis/sim/venue/FakeVenue.java:1` — failure outcomes and normalized fact behavior.

### Relevant Documentation

- https://bybit-exchange.github.io/docs/v5/websocket/trade/guideline — trade auth, command envelopes, asynchronous acknowledgements and rate feedback.
- https://bybit-exchange.github.io/docs/v5/ws/connect — private WebSocket authentication/heartbeat.
- https://bybit-exchange.github.io/docs/v5/websocket/private/order — authoritative order states.
- https://bybit-exchange.github.io/docs/v5/websocket/private/execution — execution identity, quantities, prices, fees and sequence.
- https://bybit-exchange.github.io/docs/v5/order/realtime — bounded open-order reconciliation.
- https://bybit-exchange.github.io/docs/v5/order/order-list — bounded history pagination and delay caveat.
- https://bybit-exchange.github.io/docs/v5/rate-limit — `10006` and limit headers.

### Patterns to Follow

- Fixed-capacity primitive arrays on order/private paths; no unbounded maps or queues.
- JSON cursors parse bytes with required-field masks, exact scaled decimals, maximum tokens, arbitrary field order and unknown-field skipping.
- Every emitted fact carries local ID, venue/instrument, session generation, receive epoch and monotonic timestamps.
- Expected protocol outcomes are explicit statuses, not exceptions.
- Secrets are copied, used only for HMAC, zeroed on close, and absent from `toString`.

---

## IMPLEMENTATION PLAN

### Phase 1: Security, profiles and deterministic outbound codec

- Add closeable Bybit credentials/HMAC signer with defensive copies and zeroization.
- Add certified order profile for venue/instrument/category/symbol/scales/receive window.
- Encode auth, ping, subscribe, IOC limit create, cancel and cancel-all JSON.
- Reuse the canonical 32-byte venue client ID for `reqId` and `orderLinkId`; reject wrong venue/session generation.
- Convert scaled longs to canonical decimal ASCII exactly, without floating point.

### Phase 2: Bounded correlation, rate state and trade responses

- Add fixed-capacity request correlation keyed by request/client ID with session generation and command type.
- Parse trade auth and command responses; distinguish accepted, rejected, duplicate request, authentication failure and `10006` rate limit.
- Emit `WRITE_ACCEPTED`, `WRITE_FAILED`, `WRITE_AMBIGUOUS`, and `RATE_LIMITED` facts only. Never infer fills from command responses.
- Track limit/capacity/reset feedback in a primitive snapshot.

### Phase 3: Authoritative private order/execution normalization

- Parse narrowly subscribed inverse order and execution topics.
- Decode canonical client IDs; reject unmatched symbol/category/session/identifier.
- Map documented order statuses to ACKNOWLEDGED/WORKING/CANCELLED/REJECTED facts.
- Parse exact quantity/price strings and emit ACTUAL fills keyed by execution identity.
- Deduplicate fast/full/repeated execution evidence in a bounded table; exact duplicates are ignored and conflicting duplicates fault the adapter.

### Phase 4: Separate session coordinators and order agent

- Implement trade and private session coordinators over separate `VenueConnectionControl` instances.
- Authenticate on transport readiness, subscribe private session only after auth, heartbeat, activity timeout, reconnect with new generation and health degradation.
- Implement a single-owner order agent draining urgent before normal commands, validating LIVE state/profile/session, correlating before write and producing ambiguous facts on disconnect.
- Provide cancel-all/kill serialization but no automatic cancel-on-disconnect until testnet semantics are certified.

### Phase 5: Bounded HTTP reconciliation contract

- Add signed REST request construction for server time, realtime/history/execution/position/wallet/fee/funding endpoints.
- Keep transport behind a narrow interface; implement bounded cursor pagination and oldest-unresolved lookback.
- Normalize authoritative order results to `RECONCILED` facts and expose completion only after all configured pages/categories are consumed.
- Surface incomplete pagination, conflicting duplicates and rate exhaustion explicitly.

### Phase 6: Offline certification and documentation

- Golden-test auth/signatures and create/cancel payloads with no secret leakage.
- Test accepted, rejected, partial/full fill, duplicate/conflicting execution, cancel race, lost ack, disconnect, reconnect generation, rate limit, malformed/oversize frame and reconciliation.
- Add fixture-based end-to-end command → trade response → private fill → fact-lane tests.
- Update Bybit capability contract with implemented versus credential-gated evidence.

---

## STEP-BY-STEP TASKS

1. **CREATE** security/profile/decimal/client-ID codec types under `basis-venue-bybit/.../order`; validate with focused unit tests.
2. **CREATE** bounded correlation, execution dedupe and rate-feedback state; validate capacity, duplicate and conflict cases.
3. **CREATE** trade response and private stream parsers using the existing JSON cursor; validate golden fixtures and malformed-field rejection.
4. **CREATE** authenticated trade/private sessions and the priority-draining order agent; validate reconnect fencing and UNKNOWN-producing ambiguity.
5. **CREATE** signed reconciliation request builder/coordinator over a fakeable transport; validate bounded pagination and complete-only results.
6. **UPDATE** capability/runbook documentation and package ownership metadata.
7. **RUN** formatting, focused tests, property tests where applicable, full Java 25 reactor verification and diff hygiene.

## TESTING STRATEGY

Unit tests cover byte-exact signing/encoding, strict parsing, fixed-capacity tables, lifecycle transitions, dedupe conflict, rate feedback and bounded pagination. Component tests use fake connections/transports and the real `PriorityOrderCommandLane`/`OrderFactLane`. No test requires credentials or a network. A future `@Tag("venue-contract")` suite will execute the same fixtures against Bybit testnet after authorization.

## VALIDATION COMMANDS

1. `./mvnw -o spotless:apply`
2. `./mvnw -o -pl basis-venue-bybit -am test`
3. `./mvnw -o -Pproperty-tests -pl basis-venue-bybit -am verify`
4. `./mvnw -o -T1C clean verify`
5. `git diff --check`

## ACCEPTANCE CRITERIA

- [ ] Secrets never appear in facts, payload diagnostics, exceptions or `toString` and are zeroed on close.
- [ ] Create/cancel IDs and exact native decimals are deterministic and bounded.
- [ ] Command response acceptance never produces a fill or terminal order state.
- [ ] Private executions produce exactly one fill fact across exact duplicates/fast-full overlap; conflicts degrade health.
- [ ] Lost response/disconnect becomes ambiguous/UNKNOWN and is never blindly resent.
- [ ] New session generations cannot inherit unresolved correlations as safe.
- [ ] Reconciliation is bounded, paginated, authoritative and complete-only.
- [ ] Urgent commands drain before normal commands; rate feedback reserves failure semantics.
- [ ] All offline tests and full reactor verification pass.
- [ ] Testnet certification remains explicitly pending credentials and authorization.

## OPEN QUESTIONS / ASSUMPTIONS

- Initial certified product is inverse `BTCUSD`; additional categories require separate profiles/fixtures.
- IOC limit is the initiating default; other TIF/order variants remain profile-controlled and require certification.
- Cancel-on-disconnect is encoded as a capability but remains disabled until its account/session scope is proven on testnet.
- Company-provided testnet credentials, account mode, fee tier, IP allowlist and permissions are unavailable, so the live exit gate cannot be claimed in this branch.

## AMENDMENTS

- Full-reactor architecture validation required a neutral authenticated-order fact
  contract in `basis-venue-api`; Bybit adapters do not import OEMS implementation
  types. `OrderFactLaneSink` owns the explicit boundary mapping.
- Runtime review added a bounded active-order registry separate from pending
  command correlations. Loss of the private authoritative stream now emits one
  `DISCONNECTED` fact per nonterminal local order, while private evidence clears
  late command correlations.
- Reconciliation consumes both realtime/open and history pages, merges bounded
  duplicate results before publishing, and preserves the session generation
  encoded in each local order ID so older unresolved orders can recover.
- The concrete HTTP transport uses an injected epoch clock and bounded streaming
  body reads to satisfy deterministic-clock and response-memory invariants.
