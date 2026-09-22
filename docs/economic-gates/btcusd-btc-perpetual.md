# Economic Gate: BTCUSD / BTC-PERPETUAL

**Status:** PREDECLARED; no pass/fail conclusion yet  
**Candidate:** Bybit `BTCUSD` inverse perpetual versus Deribit `BTC-PERPETUAL`

## Hypothesis

At one or more conservative size buckets, an aggressive initiating IOC followed
by a fill-driven aggressive hedge has positive normalized net edge often enough
and for long enough to be executable after every cost and uncertainty reserve.

## Predeclared calculation

For each direction and canonical BTC-delta target:

```text
netEdge = normalized executable proceeds
        - normalized executable cost
        - taker fees on both legs
        - expected funding over the holding horizon
        - conservative slippage and liquidity haircut
        - latency risk conditional on observed path percentile
        - conversion/collateral cost
        - model uncertainty and safety reserve
```

VWAP uses executable opposite-side depth. Native quantities are selected by the
certified inverse payoff and hedge-ratio model with risk-increasing rounding
forbidden. Midpoint spread and gross spread are not decision metrics.

## Dataset and split

- At least seven representative production days, extended until calm, active,
  and volatility-burst regimes are present.
- Concurrent Bybit and Deribit frames timestamped by the same process-local
  monotonic clock.
- Training windows calibrate haircut, size buckets, age/skew hypotheses, and
  latency reserve. Holdout windows are evaluated once without retuning.
- Every source file, configuration, calculation version, and output is hashed.

## Pass criteria

- Positive conservative net edge on holdout after all costs and reserves.
- Opportunity duration exceeds the measured decision-to-wire and hedge path
  with sufficient margin at the approved percentile.
- Results remain positive under worse-fee, worse-slippage, and delayed-hedge
  sensitivity cases.
- The selected size is supported by both legs without optimistic reuse of depth.
- No unsupported unit, fee, funding, or conversion assumption remains.

## Automatic fail conditions

- Profit exists only before fees/funding or only at midpoint.
- Holdout becomes positive only after relaxing a predeclared safety reserve.
- Required Deribit feed entitlement is unavailable and the authorized fallback
  destroys the edge.
- Cross-leg skew or latency tails make observed opportunities non-executable.
- Payoff/quantity reconciliation differs from venue truth.

## Evidence still required

Account fee schedules, funding histories, simultaneous production captures,
candidate-host path measurements, payoff-oracle validation, and holdout report.

