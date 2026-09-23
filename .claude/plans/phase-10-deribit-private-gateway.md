# Feature: Phase 10 Deribit private stream and order gateway

This plan implements `docs/implementation-plan.md` Phase 10 on the merged Phase 9 mainline. It inherits the cell-first architecture, separate order/private sockets, fixed-capacity ownership, neutral venue-order fact boundary, explicit UNKNOWN state, generation fencing, and complete-only reconciliation rules already established by Phases 6–9.

## Feature Description

Build the Deribit JSON-RPC v2 authenticated order and private-data boundary for the initial inverse `BTC-PERPETUAL` profile. The adapter authenticates independent order/private WebSockets with signed credentials, correlates pipelined requests by monotonic IDs, encodes exact product-native amounts and prices, normalizes authoritative private order/trade notifications, deduplicates trades in currency scope, refreshes tokens, answers heartbeat test requests, tracks rate errors, and reconciles unresolved orders through bounded authenticated HTTP queries.

## User Story

As the execution-cell owner, I want Deribit orders and fills to resolve through authoritative, generation-fenced evidence so that out-of-order responses, token expiry, reconnects, duplicate trades, and lost acknowledgements cannot create hidden exposure or blind retransmission.

## Problem Statement

The repository has Deribit public market data and a complete Bybit authenticated gateway, but no private/order implementation for the hedge venue. The OMS therefore cannot execute both legs or reconcile Deribit exposure after uncertainty.

## Solution Statement

Add a `com.penguinsecure.basis.venue.deribit.order` package mirroring Phase 9's safety boundaries without copying Bybit protocol assumptions. A closeable credential signer produces `client_signature` authentication; a closeable token state owns OAuth tokens. A deterministic JSON-RPC encoder uses monotonic IDs and canonical 32-byte labels. Fixed-capacity correlation, active-order, venue-order-ID and trade-deduplication tables normalize private evidence into `VenueOrderFactSink`. Separate session coordinators own order and private sockets, token refresh, heartbeat, subscription, reconnect and generation. A bounded warm HTTP transport and reconciliation coordinator query recent/open order truth and publish merged complete-only facts.

## Recommended Direction

Implement the complete offline protocol boundary now and keep actual testnet authentication/execution disabled until the company provides a dedicated withdrawal-disabled Deribit testnet subaccount and explicitly authorizes order tests. Use signed `client_signature` authentication rather than transmitting the client secret as `client_credentials`.

## Out of Scope / Non-Goals

- No mainnet order transmission or credential files in source.
- No Phase 11 application configuration, lifecycle wiring, operator API, or arming.
- No claim that fixtures constitute Deribit testnet certification.
- No Starbase/FIX adapter and no shared order/private socket.
- No automatic cancel-on-disconnect enablement until its connection/account scope is observed on testnet.
- No generic multi-product converter; additional linear, dated, or option profiles require separate exact-scale/payoff certification.

## Feature Metadata

**Feature Type**: New safety-critical venue capability  
**Estimated Complexity**: Very high  
**Primary Systems Affected**: `basis-venue-deribit`, `basis-venue-api` fact boundary usage, venue contracts/docs  
**Dependencies**: Java 25, Netty 4.2, Deribit JSON-RPC v2 WebSocket/HTTP API, Phases 6–9

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 10 / task 15  
**Back-references**: `.claude/plans/phase-9-bybit-private-gateway.md`, `docs/component-design.md` section 14  
**Forward-references**: Phase 11 application assembly; Phase 12 credentialed testnet certification

---

## CONTEXT REFERENCES

### Relevant Codebase Files

- `docs/implementation-plan.md:704` — normative Phase 10 tasks and exit gate.
- `docs/component-design.md:640` — venue gateway ownership and Deribit-specific rules.
- `basis-venue-api/.../order/MutableVenueOrderFact.java` — architecture-safe fact boundary.
- `basis-venue-bybit/.../order/BybitOrderAgent.java` — command/ambiguity pattern, not protocol semantics.
- `basis-venue-bybit/.../order/BybitAuthenticatedSession.java` — separate-session lifecycle pattern.
- `basis-venue-deribit/.../marketdata/DeribitNettyWebSocketConnection.java` — bounded Netty transport seam.
- `basis-venue-deribit/.../marketdata/DeribitBoundedBookParser.java` — bounded cursor/exact decimal pattern.
- `basis-core/.../identity/VenueClientIdEncoder.java` — canonical 32-byte label encoding.

### Relevant Documentation

- https://docs.deribit.com/api-reference/authentication/public-auth — signed authentication and token refresh.
- https://docs.deribit.com/articles/json-rpc-overview — response/error/ID envelopes.
- https://docs.deribit.com/api-reference/session-management/public-set_heartbeat — heartbeat and `test_request`.
- https://docs.deribit.com/subscriptions/user/userordersinstrument_nameraw — authoritative order channel.
- https://docs.deribit.com/subscriptions/user/usertradesinstrument_nameraw — authoritative trade channel.
- https://docs.deribit.com/api-reference/trading/private-buy — exact amount/price/label/TIF fields.
- https://docs.deribit.com/api-reference/trading/private-get_user_trades_by_instrument_and_time — bounded trade history.
- https://docs.deribit.com/articles/errors — token expiry `13009`, rate limit `10028`, and trading errors.

### Patterns to Follow

- Venue implementation packages depend on neutral `basis-venue-api` order facts, never OEMS internals.
- Fixed-capacity primitive/byte arrays on WebSocket paths; no unbounded request maps or queues.
- Parse arbitrary JSON field order, bound nesting/token/frame sizes, skip unknown fields, and reject duplicate required fields.
- JSON-RPC IDs are monotonic and responses are correlated by ID, never arrival order.
- Labels are canonical local IDs; venue `order_id` is separately mapped and never treated as identity authentication.
- Trade response acceptance is not a fill. Private trade identity/content is authoritative.
- Secrets and OAuth tokens are defensive copies, redacted, and zeroed on close/replacement.
- Expected wire failures return explicit statuses; socket handlers degrade health without logging payloads.

---

## IMPLEMENTATION PLAN

### Phase 1: Security, profile and deterministic JSON-RPC encoding

- Add closeable HMAC credentials and closeable token state with zeroization/redaction.
- Add the initial inverse `BTC-PERPETUAL` order profile with exact amount/price scales and currency scope.
- Add a monotonic non-reusing request-ID sequence and secure nonce seam.
- Encode signed auth, refresh, heartbeat/test, private subscriptions, IOC buy/sell, edit, cancel, cancel-all, and cancel-on-disconnect requests.
- Use canonical 32-byte local IDs as Deribit labels and exact scaled ASCII without floating point.

### Phase 2: Correlation, response parsing and rate state

- Add a fixed-capacity correlation table keyed by JSON-RPC ID with command/local/session metadata.
- Parse auth/refresh/subscription/order response and error envelopes independent of field order.
- Treat error `10028` as rate-limited and `13009` as authentication expiry; preserve other error codes.
- Publish submit write acceptance only on transport write, rejects/rate failures from correlated responses, and ambiguity for unresolved disconnects.

### Phase 3: Authoritative private order/trade normalization

- Parse `user.orders.<instrument>.raw` and `user.trades.<instrument>.raw` notifications.
- Decode/validate labels, instrument and session generation; map venue order IDs for trade correlation.
- Map open/cancelled/rejected order states and normalize exact amount/price fills.
- Deduplicate scoped trade IDs by exact identity/content; ignore exact duplicates and fail on conflict/capacity.
- Track active locally-owned orders separately from pending requests so private-socket loss emits UNKNOWN evidence exactly once.

### Phase 4: Separate authenticated sessions and handlers

- Implement independent ORDER and PRIVATE session coordinators over distinct connections.
- Authenticate, configure heartbeat, subscribe only after auth, refresh before token expiry, answer `test_request`, enforce activity timeout, and reconnect with a new generation.
- Implement order/private Netty handlers that route response versus subscription notifications and degrade health on malformed/conflicting input.
- Keep cancel-on-disconnect encoding present but disabled by default pending testnet evidence.

### Phase 5: Bounded HTTP reconciliation

- Add explicit testnet/mainnet endpoints and authenticated HTTP transport with bounded streaming responses.
- Add endpoint definitions for open/history/trades/positions/account/instrument/funding/server-time/fee evidence.
- Parse and merge bounded open/history order pages before publishing `RECONCILED` facts.
- Preserve generations encoded in labels so older UNKNOWN orders can resolve after reconnect/restart.
- Surface transport failure, rate/auth rejection, result conflict, capacity and page-limit exhaustion explicitly.

### Phase 6: Offline certification and documentation

- Golden-test signatures and exact request bytes without secret/token leakage.
- Test out-of-order responses, response-before/private and private-before-response, partial/full fills, duplicate/conflicting trades, cancel race, lost acknowledgement, both socket disconnects, token refresh, rate error, malformed input and reconciliation.
- Update Deribit capability contract and add a credentialed testnet certification runbook.

---

## STEP-BY-STEP TASKS

1. **CREATE** Deribit security/profile/request-ID/decimal/encoder types and golden tests.
2. **CREATE** bounded correlation, active-order, venue-order and trade-dedupe tables with capacity/conflict tests.
3. **CREATE** JSON-RPC response and private notification parsers using `JsonByteCursor`; validate arbitrary order and strict identity.
4. **CREATE** authenticated session, order agent and Netty handlers; validate refresh, reconnect and UNKNOWN behavior.
5. **CREATE** bounded HTTP reconciliation request/response/transport/coordinator; validate complete-only pagination/merge.
6. **UPDATE** Deribit capability contract and **CREATE** the Phase 10 certification runbook.
7. **RUN** formatting, focused tests, property profile, architecture tests, full Java 25 reactor verification and diff hygiene.

## TESTING STRATEGY

Unit tests use fixed clocks/nonces, fake connections, real neutral fact-lane adaptation, exact JSON fixtures and fake reconciliation transports. No unit test requires credentials or network. Tests cover fixed-capacity exhaustion, arbitrary response order, out-of-order request completion, wrong venue/session labels, trade-ID scope/content conflict, token replacement/expiry, heartbeat test responses, open/history merging and private-disconnect ambiguity. Future `venue-contract` tests will run the same scenarios against Deribit testnet.

## VALIDATION COMMANDS

1. `./mvnw -o spotless:apply`
2. `./mvnw -o -pl basis-venue-deribit -am test`
3. `./mvnw -o -Pproperty-tests -pl basis-venue-deribit -am verify`
4. `python3 -m unittest discover -s tools/tests -v`
5. `./mvnw -o -T1C clean verify`
6. `git diff --check`

## ACCEPTANCE CRITERIA

- [x] Credentials/tokens never appear in diagnostics/facts and stored copies are zeroed on close/replacement.
- [x] Request IDs never repeat in process lifetime and out-of-order responses correlate correctly.
- [x] Exact native amount/price and canonical labels are deterministic for every admitted command.
- [x] Command responses never invent fills; private trades produce exactly one fill per scoped trade identity.
- [x] Pending-write disconnect and private-truth disconnect resolve to explicit ambiguity/UNKNOWN without retransmission.
- [x] Token refresh, heartbeat test response, activity timeout, reconnect generation and rate/auth errors are explicit states.
- [x] Reconciliation is bounded, consumes open/history evidence, preserves old generations and publishes only after complete collection.
- [x] All offline tests, architecture rules and full reactor verification pass.
- [x] Live testnet certification remains explicitly pending credentials and authorization.

## OPEN QUESTIONS / ASSUMPTIONS

- Initial profile is inverse `BTC-PERPETUAL`: native amount scale 0 (USD units), price scale 2; runtime metadata must still certify tick/minimum/contract economics.
- IOC limit is the initiating default. Edit/post-only/reduce-only encoders exist for certification but are not reachable from the current common command enum beyond submit/cancel.
- `client_signature` HMAC uses `timestamp + "\\n" + nonce + "\\n" + data`; a secure nonce source is required outside deterministic tests.
- Separate ORDER and PRIVATE WebSockets each authenticate and own independent token state.
- Company testnet credentials, scopes, account settings, rate tier and COD configuration are unavailable; live behavior cannot be certified in this branch.

## AMENDMENTS

- Implementation keeps portfolio notifications explicitly non-authoritative at
  the order-fact boundary; Phase 11 owns account/position state assembly.
- Private order/trade evidence completes a pending JSON-RPC correlation by local
  identity so private-before-response ordering cannot later create false write
  ambiguity.
