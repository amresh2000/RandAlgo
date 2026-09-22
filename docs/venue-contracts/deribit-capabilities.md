# Deribit Capability Contract

**Evidence date:** 2026-09-22  
**Scope:** Public metadata and bounded public order-book inputs for Phase 0  
**Evidence labels:** DOCUMENTED, OBSERVED, INFERRED, UNKNOWN

## Admitted endpoints and profiles

| Capability | Contract | Evidence |
|---|---|---|
| Instrument metadata | `public/get_instrument`, `public/get_instruments` | OBSERVED against production |
| Public JSON-RPC feed | `wss://www.deribit.com/ws/api/v2` | DOCUMENTED; short Phase 0 smoke observed |
| Bounded book | `book.<instrument>.none.20.<interval>` | DOCUMENTED; codec/replay fixture tested; production gate pending |
| Standard interval | `100ms` | DOCUMENTED |
| Finest JSON interval | `raw`, nominal 1 ms aggregation | DOCUMENTED; authorized users only |
| Order/private isolation | Separate connections to avoid TCP head-of-line blocking | DOCUMENTED |

The bounded feed is treated as a complete top-N image only after captured wire
evidence proves that semantic. Until then it is not admitted to a trustworthy
production book. The adapter enforces this with a default-off
`completeImageCertified` profile capability rather than relying on operator
convention. The full-depth incremental channel is a separate contract.

The Phase 3 parser fixture at
`basis-sim/src/test/resources/wire/market-data/deribit-bounded-20-image.json`
is a sanitized official-documentation example, not proof from a production
capture. Its hash is pinned by the adjacent `SHA256SUMS` file and replayed through
the production parser to a stable normalized digest.

The live public feed serializes some price and amount values in scientific
notation (observed examples include `5.0e3` and `4.54e4`). The parser accepts
such values only when they convert exactly to configured integer ticks or lots;
inexact or overflowing values fail closed without floating-point conversion.

## Observed representative instruments

| Instrument | Product | Quote/settlement | Tick | Native amount | Contract size | Expiry |
|---|---|---|---:|---:|---:|---:|
| `BTC-PERPETUAL` | Reversed perpetual | USD/BTC | 0.5 USD | USD units | 10 USD | perpetual |
| `BTC-25DEC26` | Reversed future | USD/BTC | 2.5 USD | USD units | 10 USD | 1798185600000 |
| `BTC-26MAR27` | Reversed future | USD/BTC | 2.5 USD | USD units | 10 USD | 1806048000000 |
| `BTC_USDC-PERPETUAL` | Linear perpetual | USDC/USDC | 0.1 USDC | BTC units | 0.0001 BTC | perpetual |

The live public metadata reported minimum trade amount 10 USD, lot size 1,
taker commission 0.00035, and maker commission 0.00015 for these examples.
Account-specific configuration remains authoritative.

## Feed evolution constraint

Deribit's current guidance schedules the legacy SBE feed for deprecation at the
end of 2026 and directs new low-latency integrations toward selected-client
Starbase. V1 continues to target bounded JSON WebSocket feeds. Starbase remains
post-v1 and cannot be substituted without a new adapter/book ADR and entitlement.

## Fail-closed requirements

- Startup compares instrument type, currencies, tick, native amount, contract
  size, minimum, expiry, lifecycle, and fees against the signed definition.
- Missing raw entitlement cannot silently fall back to `100ms`; a strategy/feed
  profile must explicitly authorize its interval and latency model.
- Cross-instrument sequence or time order is never inferred.
- A malformed image, reconnect, failed publication, impossible book, or stale
  deadline revokes new exposure.

## Outstanding Phase 0 evidence

- Capture proof for bounded top-N image semantics and reconnect behavior.
- Raw-feed and account entitlements.
- Account-specific fees, limits, and private reconciliation behavior.
- Measured endpoint RTT/jitter from candidate regions.

## Sources

- https://docs.deribit.com/subscriptions/orderbook/bookinstrument_namegroupdepthinterval
- https://docs.deribit.com/articles/market-data-collection-best-practices
- https://docs.deribit.com/articles/order-management-best-practices
- https://www.deribit.com/api/v2/public/get_instrument
