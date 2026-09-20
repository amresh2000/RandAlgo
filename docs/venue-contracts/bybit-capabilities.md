# Bybit Capability Contract

**Evidence date:** 2026-09-20  
**Scope:** Public metadata and bounded public order-book inputs for Phase 0  
**Evidence labels:** DOCUMENTED, OBSERVED, INFERRED, UNKNOWN

## Admitted endpoints and profiles

| Capability | Contract | Evidence |
|---|---|---|
| Instrument metadata | `GET /v5/market/instruments-info` | OBSERVED against production |
| Inverse public feed | `wss://stream.bybit.com/v5/public/inverse` | DOCUMENTED; capture pending |
| Bounded book | `orderbook.50.<symbol>`, nominal 20 ms | DOCUMENTED; capture pending |
| Book updates | Initial snapshot, deltas, later snapshot replaces local state | DOCUMENTED |
| Sequence evidence | Preserve `u`, `seq`, `ts`, and `cts` independently | DOCUMENTED |
| Order entry/private data | V5 trade WebSocket plus separate private WebSocket | DOCUMENTED; Phase 9 certification |

No exact `u + 1` continuity rule is admitted. A received snapshot replaces the
book. A zero delta quantity deletes the level. Observed reconnect, duplicate,
restart, and gap behavior must be added from captures before Phase 3.

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

## Sources

- https://bybit-exchange.github.io/docs/v5/websocket/public/orderbook
- https://bybit-exchange.github.io/docs/v5/websocket/trade/guideline
- https://bybit-exchange.github.io/docs/v5/websocket/private/execution
- https://api.bybit.com/v5/market/instruments-info
