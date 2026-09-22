# Bybit Capability Contract

**Evidence date:** 2026-09-22  
**Scope:** Public market data plus the offline-certified authenticated boundary through Phase 9  
**Evidence labels:** DOCUMENTED, OBSERVED, INFERRED, UNKNOWN

## Admitted endpoints and profiles

| Capability | Contract | Evidence |
|---|---|---|
| Instrument metadata | `GET /v5/market/instruments-info` | OBSERVED against production |
| Inverse public feed | `wss://stream.bybit.com/v5/public/inverse` | DOCUMENTED; short Phase 0 smoke observed |
| Bounded book | `orderbook.50.<symbol>`, nominal 20 ms | DOCUMENTED; codec/replay fixture tested |
| Book updates | Initial snapshot, deltas, later snapshot replaces local state | DOCUMENTED |
| Sequence evidence | Preserve `u`, `seq`, `ts`, and `cts` independently | DOCUMENTED |
| Order entry/private data | V5 trade WebSocket plus separate private WebSocket | DOCUMENTED; Phase 9 fixture-certified, live testnet pending |
| Reconciliation | Signed V5 REST order realtime/history pages | DOCUMENTED; bounded parser/transport fixture-certified |

## Phase 9 authenticated boundary

The adapter now has separate trade and private session coordinators, strict
authentication/subscription parsers, deterministic IOC create/cancel encoders,
fixed-capacity correlation and execution-deduplication tables, authoritative
private order/execution normalization, rate feedback, and signed bounded HTTP
order reconciliation. A trade-command success is only transport acceptance;
order and execution streams remain authoritative. An unresolved write or a
rejected cancel becomes `WRITE_AMBIGUOUS` and enters the existing UNKNOWN /
reconciliation path rather than being retransmitted.

The initial admitted order profile is inverse `BTCUSD`, integer contract
quantity, price scale 2, and a 5,000 ms receive window. This is an implementation
profile, not authorization to trade. Additional symbols/categories require a
separate certified profile.

Fixture certification covers exact HMAC/request bytes, credential redaction and
zeroization, private control messages, command correlation, order/execution
facts, duplicate/conflicting execution evidence, reconnect ambiguity, REST
pagination, cursor encoding, and fail-closed malformed inputs. Mainnet order
transmission is not assembled in Phase 9.

Live testnet certification is still pending company-provided, withdrawal-disabled
credentials and explicit authorization. The outstanding checks are recorded in
`docs/phase-9-bybit-gateway-runbook.md` and must pass before this capability is
marked production-certified.

No exact `u + 1` continuity rule is admitted. A received snapshot replaces the
book. A zero delta quantity deletes the level. Observed reconnect, duplicate,
restart, and gap behavior still require representative captures before production
certification. Phase 3 therefore preserves the native evidence and revokes health
on malformed input, disconnect, or failed publication without inventing a gap rule.

A live public `orderbook.50.BTCUSD` run also produced bounded-window deltas whose
delete target was no longer retained locally. The observation runner therefore
uses the core book's explicit bounded-delta mode: unknown deletes are ignored,
in-window inserts evict the worst retained level, and worse-than-window inserts
are ignored. Strict full-depth consumers retain the original fail-closed behavior.

The Phase 3 parser fixture at
`basis-sim/src/test/resources/wire/market-data/bybit-orderbook-50-snapshot.json`
is a sanitized official-documentation example, not a production capture. Its hash
is pinned by the adjacent `SHA256SUMS` file and replayed through the production
parser to a stable normalized digest.

## Observed representative instruments

| Symbol | Product | Quote/settlement | Tick | Quantity step | Delivery |
|---|---|---|---:|---:|---:|
| `BTCUSD` | Inverse perpetual | USD/BTC | 0.10 USD | 1 contract | perpetual |
| `BTCUSDZ26` | Inverse future | USD/BTC | 0.50 USD | 1 contract | 1798185600000 |
| `BTCUSDH27` | Inverse future | USD/BTC | 0.50 USD | 1 contract | 1806048000000 |
| `BTCPERP` | Linear perpetual | USDC/USDC | 0.10 USDC | 0.001 BTC | perpetual |

The inverse perpetual and futures use integer native quantity, but the exact
payoff per native unit must be certified against Bybit's contract specification
and a slow payoff oracle. Metadata similarity is not sufficient authorization.

## Fail-closed requirements

- Startup compares status, product kind, currencies, tick, quantity step,
  minimums, delivery time, and settlement against a signed definition.
- `PreLaunch`, non-trading, or changed metadata blocks the strategy.
- Unknown or expired fee/funding input blocks pricing; metadata funding caps are
  not a substitute for actual funding or account fee schedules.
- RPI liquidity is absent from the documented ordinary order-book stream and
  cannot be assumed executable.
- A malformed frame, reconnect, failed publication, impossible book, or stale
  deadline revokes new exposure.

## Outstanding Phase 0 evidence

- Production and testnet captures covering reconnect, duplicate snapshot,
  quiet period, volatility burst, and service restart behavior.
- Account-specific maker/taker fees, account mode, order limits, and entitlement.
- Measured endpoint RTT/jitter from candidate regions.
- Exact amount/payoff examples reconciled to venue calculations.

## Outstanding Phase 9 evidence

- Authenticated testnet trade/private connection and heartbeat/reconnect captures.
- Create, partial/full fill, cancel race, reject, lost response, and rate-limit observations.
- Account mode, order caps, fee tier, IP allowlist, and cancel-on-disconnect scope.
- REST reconciliation agreement with private-stream facts across reconnect.

## Sources

- https://bybit-exchange.github.io/docs/v5/websocket/public/orderbook
- https://bybit-exchange.github.io/docs/v5/websocket/trade/guideline
- https://bybit-exchange.github.io/docs/v5/websocket/private/execution
- https://api.bybit.com/v5/market/instruments-info
