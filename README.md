# Basis OMS

Greenfield Java trading platform for onboarding and operating low-latency
cross-venue basis strategies between Bybit and Deribit.

The repository is in phased implementation. Phase 0 records feasibility
evidence and Phase 1 establishes the reproducible engineering foundation; no
production trading capability is implied by the build scaffolding.

## Build

Java 25 is required. Maven itself is pinned by the wrapper, so the canonical
clean-checkout gate is:

```bash
./mvnw -T1C clean verify
```

The default build is hermetic. Property, integration, replay, chaos, venue
contract, and benchmark tests are opt-in profiles documented in
`config/README.md`. The network-backed NVD audit is intentionally separate
from normal verification and requires an NVD API key:

```bash
NVD_API_KEY=... ./mvnw -Pnvd-audit verify
```

## Public market-data latency runner

Build and run the observation-only Bybit and Deribit public-feed path:

```bash
./mvnw -pl basis-app -am package
java -jar basis-app/target/basis-market-data.jar \
  --venue=both \
  --duration-seconds=60 \
  --report-seconds=5
```

No account or credentials are required. The runner subscribes to Bybit inverse
`orderbook.50.BTCUSD` and Deribit bounded
`book.BTC-PERPETUAL.none.20.100ms`, normalizes events, publishes and drains the
production SPSC lanes, applies the production fixed-depth books, and reports
stage percentiles. `--duration-seconds=0` runs until interrupted.

Run it on a time-synchronized host. Local monotonic stage timings remain valid
without wall-clock synchronization, but `venue->receive` does not.

## Golden sources

- [Architecture](docs/architecture.md) - accepted system boundaries and design decisions.
- [Component design](docs/component-design.md) - normative per-component contracts, data structures, ownership, failure behavior, and low-latency choices.
- [Implementation plan](docs/implementation-plan.md) - phase order, acceptance gates, tasks, and validation.
- [Advanced execution design](docs/advanced-execution-design.md) - post-v1 passive-maker and venue-proximate-cell contracts and promotion gates.

If implementation experience conflicts with the architecture, record the
change as an ADR before changing the implementation. Venue documentation and
observed wire behavior override assumptions in these documents.
