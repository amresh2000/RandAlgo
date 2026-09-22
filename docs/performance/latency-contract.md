# Phase 0 Latency Contract

## Clock rules

- `monotonic_ns` measures local age, skew, deadlines, and stage durations.
- Epoch time is retained for audit only.
- Venue timestamps remain separate facts and are never subtracted across venues.
- Monotonic timestamps from different hosts are never compared.

## Required stages

```text
T1  network callback/read start
T2  decode and validation complete
T3  ingress publication
T4  core dequeue
T5  book apply and trust complete
T6  executable pricing complete
T7  strategy decision complete
T8  risk reservation and OEMS complete
T9  outbound publication
T10 order-agent dequeue
T11 serialization/signing complete
T12 write/flush invoked
T13 transport write completion
```

Private-fill-to-hedge measurement begins at the private stream's T1 and ends at
the hedge order's T13. T13 is local transport completion, not venue acceptance.

## Certification gates

- Both leg ages and their same-process receive-time skew are evaluated at the
  decision and again at risk reservation.
- Thresholds are specific to strategy and feed profile. They remain hypotheses
  until production capture and target-host calibration.
- Reports include p50, p90, p99, p99.9, p99.99, maximum, queue age, throughput,
  and coordinated-omission-aware methodology.
- Any non-healthy hedge path blocks new exposure while preserving reserved
  hedge, cancel, and unwind capacity.

## Phase 0 capture contract

The disposable capture tool records `receiveEpochNanos`, `receiveMonoNanos`, the
source, and the complete public payload in one JSONL record per message. Bybit
and Deribit are captured concurrently in one process. Each run emits a manifest
with endpoint, subscription, byte count, SHA-256 digest, and errors.

The tool establishes receive-age/skew evidence. It does not claim T1-T13
production latency because the Java execution path does not yet exist.

## Threshold status

No universal millisecond threshold is authorized in Phase 0. Candidate limits
must be published with the economic analysis and recalibrated on the selected
production host during Phases 13 and 14.

