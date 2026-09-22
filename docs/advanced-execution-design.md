# Advanced Execution Design - Passive Maker and Regional Cells

**Status:** Post-v1 design constraints; not authorized for implementation  
**Last reviewed:** 2026-09-20  
**Prerequisites:** Certified aggressive engine, production shadow evidence, and
approved ADR/implementation plan  
**Parent documents:** [Architecture](architecture.md),
[Component Design](component-design.md), and
[Implementation Plan](implementation-plan.md)

This document specifies the intended shape of two major extensions without
putting their complexity into v1:

1. passive maker initiation with aggressive fill-driven hedging; and
2. venue-proximate Bybit and Deribit execution cells.

The immediate v1 additions that enable both extensions - cross-leg temporal
coherence, complete stage latency, and hedge-path health - are normative now in
the parent documents.

## 1. Priority and scope

| Capability | Aggressive v1 | Before passive maker | Before regional cells |
|---|---:|---:|---:|
| Per-leg freshness and cross-leg skew gate | required | required | per-cell contract required |
| Market-data-to-wire stage latency | required | required | required per cell and route |
| Private-fill-to-hedge-wire latency | required | required | required end-to-end across cells |
| Hedge-path health as risk input | required | required | includes route health |
| Passive quote/cancel/edit policy | out | required | optional |
| Queue and adverse-selection model | out | required | optional |
| Dual venue-proximate cells | out | optional | required |

Changing an IOC to `PostOnly` is not a passive strategy. Splitting the process
into two cells is not a latency optimization by itself. Each extension requires
its own economic thesis, fault model, and certification.

## 2. Geography and physical limits

At this review date, Bybit's official API FAQ identifies AWS Singapore
availability zones, while Deribit's documentation identifies its primary
infrastructure in London/Equinix LD4. This makes a single server simultaneously
venue-local to both impossible.

These locations MUST be revalidated before deployment. DNS, Internet routing,
front-door endpoints, matching engines, private feeds, and account-specific
connectivity may have different paths. The deployment decision uses measured
RTT/jitter and packet-path evidence from candidate hosts, not geography alone.

For a Deribit-maker/Bybit-hedge strategy, a single cell creates one unavoidable
wide-area critical path in either placement:

```text
Singapore cell: Deribit fill travels London -> Singapore, then local Bybit hedge
London cell:    local Deribit fill, then hedge command travels London -> Singapore
```

Dual cells do not repeal physics. They move the wide-area hop onto a controlled,
observable inter-cell channel while keeping each exchange session local.

## 3. Single-cell placement decision

Phase 0/13 measures every candidate region using the exact account endpoints and
feed/order profiles:

- public and private feed RTT, jitter, disconnect rate, and update age;
- order-write to acknowledgement/fill distributions;
- maker-fill receive to hedge-wire latency;
- basis half-life and opportunity duration;
- hedge slippage conditional on latency percentile;
- packet loss/reordering and route changes;
- operational support, time synchronization, storage, and failure domains.

The selected region minimizes expected risk-adjusted execution loss rather than
the arithmetic mean of two pings. If one leg is passive, learning its uncertain
fill early and reaching the hedge reliably may outweigh public-feed symmetry.

## 4. Dual-cell promotion gate

Regional cells are promoted only if all conditions hold:

```text
measured incremental net P&L from venue-local sessions and lower tails
>
infrastructure cost + engineering cost + distributed-state risk reserve
```

Required evidence:

- single-cell production shadow and bounded live data show geography is a
  material source of missed edge, adverse selection, or hedge slippage;
- a replayable idempotent inter-cell protocol passes partition, duplicate,
  reorder, process-kill, and reconciliation tests;
- each cell can fail closed without synchronous global control;
- operators rehearse route loss, split brain, delayed hedge result, and recovery;
- capital/risk owners approve the larger failure surface.

"Phase 16 reached" is not a sufficient promotion reason.

## 5. Regional topology and ownership

```text
                         GLOBAL CONTROL
                signed config and risk envelopes
                           /       \
                          v         v
              Deribit cell         Bybit cell
               London/LD4        Singapore region
                    |                  |
                 Deribit            Bybit
                    \                  /
                     controlled route
```

There is one execution-group authority. For passive/aggressive trading, it is
normally the maker-side cell because the maker fill is the uncertain event that
creates exposure. The global controller allocates fenced risk but is never a
synchronous dependency of a hedge.

The hedge-side cell owns:

- its venue session and generation;
- its freshest local hedge book and economic inputs;
- native contract/quantity conversion;
- local preauthorized risk/rate capacity;
- hedge order state, fills, and local reconciliation.

The maker-side authority owns:

- maker quote/order and fill attribution;
- execution-group canonical target and residual exposure;
- monotonically increasing maker-fill sequence;
- outstanding logical hedge requirements;
- group terminal decision after both cell states reconcile.

Neither cell may write the other's state.

## 6. Canonical hedge requirement

The maker cell sends a requirement, not a stale remote venue order:

```text
HedgeRequirement
  protocolVersion
  authorityCellId and authorityGeneration
  hedgeCellId and expectedHedgeGeneration
  executionGroupId and strategyGeneration
  makerChildOrderId and makerFillSequence
  cumulativeCanonicalDeltaFilled
  cumulativeCanonicalDeltaRequired
  incrementalCanonicalDeltaRequired
  riskCurrency and payoffModelId
  maximumNormalizedHedgeCost
  worstAllowedResidualDelta
  senderEpochNanos, validityNanos, maximumClockErrorNanos
  senderMonoNanos for trace only, traceId
  source book/fill evidence and configuration hash
```

The hedge cell uses its local book, current conversion, and certified hedge-ratio
model to choose native quantity, worst marketable price, and order parameters.
It rejects requirements whose generation, deadline, model, local data, route,
risk envelope, or cost bound is invalid.

The receiver MUST NOT compare the sender's monotonic timestamp with its own.
Expiry uses synchronized epoch time only while the measured combined clock-error
bound is inside the protocol limit; otherwise the route is unsafe. The receiver
also applies a local monotonic processing deadline after receipt.

This keeps the final executable choice close to the hedge venue and avoids
shipping a price/quantity calculated from an old remote book.

### Remote hedge evidence across clock domains

The maker cell still needs conservative hedge economics to decide whether and
where to quote. The hedge cell therefore publishes bounded `HedgeCapacityQuote`
messages containing:

```text
hedgeCell/session/configuration generations
instrument and payoff/conversion model generations
local book epoch/sequence and feed profile
size buckets with executable buy/sell cost and worst price
available hedge/rate/risk capacity generation
source receive epoch, publish epoch, maximum clock error
validity bound and message sequence
```

The maker cell records its own local monotonic receipt time and route sequence.
It MUST NOT subtract the two cells' monotonic values. It admits remote evidence
only when the synchronized epoch uncertainty is bounded and a conservative upper
bound on source age plus route age is inside the strategy contract. If clock
uncertainty is too large, the route becomes unsafe.

This is not proof that the two books represent an identical instant. The maker
policy prices the uncertainty explicitly and the hedge cell revalidates its
current local book/capacity before accepting a `HedgeRequirement`. A stale remote
capacity quote therefore prevents maker exposure but can never force the hedge
cell to execute stale native instructions.

## 7. Idempotency and delivery semantics

The logical key is:

```text
(authorityGeneration, executionGroupId, makerFillSequence, hedgeGeneration)
```

The hedge cell reserves risk and emits a critical, replayable acceptance fact for
the key before sending a venue order. A retransmission returns the reconstructed
or current result. Same key with different content is a protocol conflict and
faults the route. As with v1 journaling, an unrecorded crash tail is resolved from
the hedge venue before the cell resumes.

The design promises **idempotent logical application**, not magical exactly-once
network delivery or exactly-one venue fill. Messages can be duplicated and an
order can remain UNKNOWN. The protocol therefore carries cumulative required and
completed canonical delta, permitting both cells to reconcile residual exposure
after lost responses, partial fills, or restart.

Result states include:

```text
RECEIVED -> VALIDATED -> RESERVED -> ORDER_PENDING -> WORKING
                                      |              |
                                      v              v
                                   UNKNOWN       PARTIAL/FILLED
                                      \              /
                                       RECONCILING
                                            |
                                     RESOLVED/FAILED
```

Every result includes the same logical key, local order ID, cumulative native and
canonical fill, possible remaining fill, residual delta, and reason.

## 8. Route health and partition behavior

```text
HEALTHY -> DEGRADED -> UNAVAILABLE -> RECONCILING -> HEALTHY
```

Health uses authenticated heartbeat age, round-trip distributions, one-way
measurements only where clock-error bounds permit, sequence continuity,
unacknowledged command age, receive window, hedge-cell progress, and recent
requirement-to-wire tails.

New maker exposure requires:

```text
maker book trusted and fresh
AND hedge book reported trusted and fresh
AND temporal/coherence contract valid
AND route HEALTHY
AND hedge cell/session generation current
AND preauthorized hedge/collateral/rate capacity
AND requirement-to-wire tail within budget
```

On route degradation the maker cell stops posting and sends cancel/edit-down for
resting makers. Cancellation does not eliminate exposure: a fill can race the
cancel. The maker cell keeps every possible fill reserved and continues to retry
the idempotent hedge requirement when route state permits.

A direct remote emergency hedge path from the maker cell is optional and disabled
unless separately certified. If enabled, ownership fencing ensures only one hedge
session/generation can act for a logical requirement. Without a certified fallback,
route loss with a late fill triggers the approved manual/emergency risk procedure.

## 9. Passive maker economic model

For maker sell and aggressive hedge buy, the minimum acceptable maker price is
derived from the entire executable chain:

```text
minimumMakerSellPrice = normalized executable hedge buy cost
                      + taker hedge fee
                      + maker fee or rebate treatment
                      + funding/carry/conversion
                      + expected hedge slippage
                      + route and latency risk
                      + adverse-selection allowance
                      + model uncertainty reserve
                      + required net profit
```

The reverse direction uses conservative signs/rounding. The maker price is not
copied from the maker best bid/ask and is not justified by midpoint spread alone.

Every `MakerQuoteDecision` records:

- both book evidence tuples, ages, and cross-leg skew;
- hedge-side executable depth and worst price for full possible maker fill;
- fees, carry, conversion, slippage, latency/adverse-selection/reserve components;
- minimum/selected maker price and native quantity;
- hedge-path and route health generations;
- queue-model version and confidence;
- quote expiry, cancel threshold, and configuration hash.

## 10. Quote dependencies and invalidation

Any of these events immediately re-evaluates a live maker quote:

- maker book mutation or trust/freshness change;
- hedge book mutation or trust/freshness change;
- cross-leg skew crossing its limit;
- fee, funding, conversion, volatility, or risk-input update/expiry;
- hedge-path latency/queue/socket/rate health change;
- route/cell generation change;
- position, fill, reservation, loss, or collateral change; and
- opportunity/quote deadline.

Re-evaluation runs from the changed input's reverse-dependency slice. If full
possible fill no longer meets minimum economics or hedge capacity, request
cancel/edit-down immediately. A periodic timer is only a safety net.

## 11. Maker order lifecycle

```text
PLANNED -> POST_PENDING -> LIVE -> CANCEL_OR_EDIT_PENDING -> TERMINAL
                |          |  \             |              ^
                |          |   \-> PARTIAL_FILL -> HEDGING-+
                |          \------> FULL_FILL ----> HEDGING-+
                \-> REJECTED/UNKNOWN       \-> UNKNOWN/RECONCILING
```

Rules:

- `POST_PENDING`, `LIVE`, cancel/edit pending, and UNKNOWN reserve maximum
  remaining maker fill plus possible hedge fill.
- A cancel acknowledgement is terminal only under the certified venue contract
  and after all fills through the relevant sequence/window are accounted for.
- A fill during cancel/edit is authoritative and creates an urgent incremental
  hedge requirement.
- Edits have their own request identity and UNKNOWN handling. Never assume an
  edit was applied because it was written to the socket.
- A price-changing edit can change queue priority and economic evidence; model it
  as a new quote generation even when the venue retains the order ID.
- Group completion requires maker order resolution, hedge order resolution, and
  residual canonical delta inside the approved terminal bound.

## 12. Post-only and venue-specific behavior

Post-only behavior belongs to the venue adapter/policy capability matrix:

- Bybit currently documents `PostOnly` as cancellation when the order would
  execute immediately or cannot rest because the market changed.
- Deribit currently documents default post-only price sliding. Strict exact-price
  behavior requires `reject_post_only=true`; an aggressive edit in reject mode
  may cancel the order.
- Deribit currently documents that decreasing quantity at the same price retains
  priority, while increasing quantity or changing price loses time priority.

These are review-date facts, not universal abstractions. Startup compares the
strategy's required semantics against the certified venue capability. An adapter
must report the actual accepted/resting price and state; policy must not infer it
from the request.

## 13. Queue-position model

With aggregated L2 data, exact queue position is unknowable. V1 passive research
therefore uses `QueueAheadEstimate` with explicit uncertainty:

```text
price, side, quoteGeneration
displayedQuantityBeforeJoin
ownDisplayedQuantity
estimatedQuantityAhead range/confidence
observed trades at price
ambiguous cancellations/deletions
last update evidence and model version
```

The model MUST NOT treat every size reduction as ahead-of-us consumption. Trade
prints can reduce estimated queue ahead under a certified matching assumption;
cancellations use conservative bounds or a learned probability calibrated only
offline. Actual fills correct the estimate but do not retroactively rewrite it.

Queue value enters the quote/edit decision because changing price usually loses
priority. A one-tick improvement is accepted only when expected incremental fill
value exceeds lost queue value, new adverse-selection risk, and request cost.

Level-3 feeds such as entitled Deribit Starbase can support a different model but
require a separate adapter/book/model ADR. L2 and L3 evidence cannot share a
confidence label.

## 14. Adverse-selection model

Maker fills are sampled conditional on subsequent hedge cost, mark/index movement,
trade sign/flow, volatility, book imbalance, queue estimate, quote age, and
fill-to-hedge latency. The model estimates conservative loss after a fill, not
merely probability of filling.

Inputs and output are versioned, freshness-gated, and bounded. An unavailable or
stale adverse-selection model stops passive posting rather than assuming zero.
Initial production policy may use conservative lookup tables; a machine-learning
model is not required and cannot bypass deterministic risk bounds.

Training and evaluation use time-separated windows. The release report includes
fill rate, realized maker rebate/fee, hedge slippage, markout at several horizons,
cancel-to-fill race rate, queue-estimate calibration, and net P&L on holdout data.

## 15. Passive risk reservations

For every live or possibly live maker child:

```text
confirmed maker fills
+ maximum remaining maker fill
+ maximum possible outstanding hedge fill in the wrong direction
<= authorized exposure
```

The system pre-reserves full hedge-side depth, collateral, and rate capacity for
the maker quantity. It may quote less than maker-book capacity when the hedge
side cannot support the full amount. If hedge liquidity or health falls, cancel/
edit-down the maker even if its own book is unchanged.

Multiple maker orders may not each reserve the same hedge liquidity optimistically.
Capacity is allocated conservatively across groups. Portfolio netting remains
out of scope until separately designed.

## 16. Passive policy rollout

1. Offline counterfactual quote generation with no orders.
2. Shadow quote/cancel decisions against production data.
3. Testnet lifecycle/fault certification, acknowledging testnet queue economics
   are not representative.
4. Mainnet post-only minimum-size quotes with one live group and strict timeout.
5. Validate fills, markouts, cancel races, hedge latency, fees, and net P&L.
6. Increase only one of quote duration, quantity, instrument count, or concurrency.

The policy returns to shadow if actual venue semantics, cancel-race rate, queue
model, adverse selection, or hedge-path tails breach the certified envelope.

## 17. Required spikes and decision rules

### Passive-maker spike

**Question:** Does passive capture remain positive after adverse selection,
queue delay, cancellations, fees, full hedge cost, and latency?  
**Experiment:** Replay production L2/trades/private fills where available, shadow
quote generations, and run minimum-size post-only canaries with full markouts.  
**Decision rule:** Implement the live policy only if holdout and canary net P&L
remain positive under conservative queue/cancel assumptions and every cancel race
converges safely.

### Regional-cell spike

**Question:** Do local venue sessions plus a controlled route improve economic
tails enough to justify distributed ownership?  
**Experiment:** Run shadow cells in candidate regions, transmit idempotent hedge
requirements without orders, inject route faults, and compare requirement-to-wire
and predicted slippage with the single-cell baseline.  
**Decision rule:** Promote only when the predeclared economic improvement exceeds
cost/risk reserve and the partition/recovery certification has no unresolved
critical finding.

## 18. References

- [Bybit server-location FAQ](https://bybit-exchange.github.io/docs/faq)
- [Bybit public order book and `cts`](https://bybit-exchange.github.io/docs/v5/websocket/public/orderbook)
- [Bybit order/post-only behavior](https://bybit-exchange.github.io/docs/v5/order/create-order)
- [Deribit market-data/location/Starbase practices](https://docs.deribit.com/articles/market-data-collection-best-practices)
- [Deribit order editing and post-only practices](https://docs.deribit.com/articles/order-management-best-practices)
