# Public Feed Capture Procedure

Run from a time-synchronized Linux host with Python 3.11 or newer:

```bash
python3 tools/capture_public_feeds.py \
  --duration-seconds 3600 \
  --output-dir basis-sim/src/test/resources/wire/captures/<utc-run-id>
```

The default profiles are Bybit inverse depth 50 for `BTCUSD` and Deribit
ungrouped depth 20 at `100ms` for `BTC-PERPETUAL`. The latter is the public
fallback, not proof that the strategy can meet its economic gate. Raw Deribit
capture is added only after entitlement is confirmed and without storing
credentials in the repository.

For each evidence set:

1. Record host, region, kernel, Python version, clock-sync status, DNS results,
   and route information alongside the generated manifest.
2. Capture both feeds in the same process for comparable monotonic timestamps.
3. Include connection establishment, at least one quiet period, active period,
   and volatility burst. Run separate controlled reconnect cases.
4. Preserve raw evidence outside Git when large; commit only reviewed,
   sanitized, checksummed fixtures needed by contract tests.
5. Reject any run whose manifest contains errors or either JSONL file is empty.
6. Never record credentials or private frames with this tool.

Validate the local frame codec before a run:

```bash
python3 -m unittest discover -s tools/tests -v
```

Exercise and measure the production Java receive/decode/lane/book path without
credentials or order-entry capability:

```bash
./mvnw -pl basis-app -am package
java -jar basis-app/target/basis-market-data.jar \
  --venue=both \
  --duration-seconds=300 \
  --report-seconds=10
```

The Java runner reports venue timestamp to local receive time, receive to
decode, publication to dequeue, receive to book completion, and book-apply
time. Treat venue-to-receive measurements as invalid unless the host wall clock
is independently verified as synchronized. The runner is observation-only and
does not load credentials, private channels, or order-entry components.

Generate the same-clock cadence/skew report:

```bash
python3 tools/analyze_public_capture.py \
  basis-sim/src/test/resources/wire/captures/<utc-run-id> \
  --output basis-sim/src/test/resources/wire/captures/<utc-run-id>/analysis.json
```

The report describes the captured sample only. It cannot establish a production
threshold until the required representative dataset and holdout analysis exist.
