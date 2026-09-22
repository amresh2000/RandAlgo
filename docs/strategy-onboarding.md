# Strategy Onboarding Contract

## Purpose

A definition binds two certified venue instruments to existing payoff, hedge,
carry, signal, execution, and risk models. It cannot bypass common market-data,
risk, OEMS, journal, or recovery behavior.

## Required definition

| Section | Required fields |
|---|---|
| Identity | strategy ID, semantic version, lifecycle, effective time, configuration hash |
| Legs | venue, instrument selector, account/session binding, side eligibility |
| Canonical risk | underlying, risk currency, payoff model per leg, hedge-ratio model, rounding rule |
| Economics | fee, funding/carry, settlement, conversion, haircut, slippage, latency-risk, reserve sources |
| Signal | entry/exit thresholds, size ladder, holding horizon, opportunity expiry |
| Execution | certified policy ID, initiating/hedging roles, worst prices, pay-up/unwind bounds |
| Market data | feed profile, depth, per-leg maximum age, maximum cross-leg receive skew |
| Performance | market-data-to-write and private-fill-to-hedge-write p99/p99.9 budgets |
| Risk | gross/net/unhedged limits, maximum imbalance, collateral, rate, loss, concurrency limits |
| Certification | fixture hashes, model versions, replay report, economic-gate report, approvals |

External decimal values are parsed on a cold path into explicit scaled integer
units. External symbols resolve to dense runtime IDs. Unknown fields, ambiguous
units, lossy rounding, stale inputs, or metadata drift reject activation.

## Onboarding tiers

1. **Definition-only:** all models and policy already certified; configuration,
   fixtures, economic evidence, replay, and certification are required.
2. **New model:** a compiled payoff/carry/signal/policy model plus ADR, oracle
   tests, properties, and benchmarks is required.
3. **New venue or asset class:** outside the accepted architecture.

## Lifecycle

```text
DRAFT -> CONTRACT_VALIDATED -> REPLAY_CERTIFIED -> SHADOW
      -> TESTNET_CERTIFIED -> CANARY -> ACTIVE

Any state -> SUSPENDED -> RETIRED
```

A material leg, model, account, threshold, feed, or risk change creates a new
version and invalidates downstream certification. Rollback activates a prior
signed version rather than mutating a live generation.

## Phase 0 candidates

| Candidate | Role in evaluation | Status |
|---|---|---|
| Bybit `BTCUSD` / Deribit `BTC-PERPETUAL` | Like-currency inverse perpetual reference | Leading hypothesis, not selected |
| Bybit `BTCUSDZ26` / Deribit `BTC-25DEC26` | Matching-expiry inverse dated future | Metadata compatible; economics pending |
| Bybit `BTCUSDH27` / Deribit `BTC-26MAR27` | Longer-horizon inverse dated future | Metadata compatible; economics pending |
| Bybit `BTCPERP` / Deribit `BTC_USDC-PERPETUAL` | Like-currency linear perpetual reference | Metadata compatible; economics pending |

The reference strategy is selected only after concurrent captures and the
predeclared economic gate. Matching symbols or expiry timestamps do not prove a
valid hedge ratio or profitable execution.
