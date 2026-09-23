# Phase 11 operator and shutdown runbook

This runbook defines the execution-cell control contract. It does not authorize
mainnet trading. Phase 12 must certify the assembled process on testnet before
any production promotion.

## Startup and arming

The process executes these steps in order and fails closed on the first
incomplete step:

1. load and validate signed configuration;
2. start the archive and verify it is writable;
3. load venue metadata;
4. connect private sessions and reconcile orders, positions, and balances;
5. synchronize and trust public books;
6. warm the strategy, risk, and order paths;
7. publish one same-clock readiness sample.

Readiness requires recovery stage `DISARMED_READY`, a positive active
configuration generation, healthy archive and watchdogs, and healthy hedge
paths. A successful restart always stops in `DISARMED_READY`; it never auto-arms.

Before sending `ARM`, the operator must verify the status response identifies:

- the expected configuration generation;
- `DISARMED_READY` lifecycle state;
- healthy archive, watchdog, hedge path, book, and private-session signals;
- no unresolved `UNKNOWN` order state or reconciliation difference.

`ARM` carries that exact configuration generation and a control generation
strictly newer than the cell's current control generation.

## Authenticated operator commands

Cold ingress verifies the request HMAC, operator identity, maximum role, issue
time, expiry, and requested role before the request enters the bounded SPSC
control lane. The signature covers every scalar field and payload byte.

| Command | Minimum role | Required generation |
| --- | --- | --- |
| status | viewer | none |
| reconcile, snapshot | operator | expected configuration |
| arm, disarm, cancel-all | trader | new control generation where applicable |
| scoped kill | risk | new kill/control generation |
| stage config, activate config, shutdown | admin | expected configuration or new control generation |

Every mutation must be accepted into the critical journal before it is applied.
The durable `OperatorControl` fact contains the unique command ID, authenticated
operator ID and role, action, scope, reason, expected configuration generation,
and control generation. Reusing a command ID returns the cached prior result and
does not repeat the mutation. Cache or lane exhaustion rejects new work.

Operator responses are asynchronous. A full response lane drops the response,
increments its drop counter, and never blocks the core. The operator may safely
retry the same command ID to retrieve the cached result.

## Signed configuration changes

Configuration changes use a versioned binary envelope containing format
version, strictly increasing generation, issue/expiry time, payload length,
payload, and a 32-byte HMAC-SHA256 signature. Stage validates the signature and
expiry without changing active runtime state. Activate succeeds only when:

- the staged generation exactly matches the command;
- it is newer than the active generation; and
- the cell is `DISARMED_READY`.

Activation advances both the staged store and lifecycle generation. Never
delete the promoted configuration artifact: its hash and generation are part of
restart and replay evidence.

## Graceful shutdown

Use a fresh admin command ID and a strictly newer control generation. A journal
admission failure means shutdown has not started and must be investigated; do
not assume the process is draining.

The state machine performs one bounded step at a time:

```text
ARMED or DISARMED_READY
  -> DRAINING
  -> RECONCILING
  -> SNAPSHOTTING
  -> FLUSHING_JOURNAL
  -> CLOSING_VENUES
  -> CLOSING_INFRASTRUCTURE
  -> STOPPED
```

`DRAINING` immediately prevents new exposure, cancels or resolves live orders,
and retains risk-reducing actions until the configured deadline. Venue order and
private channels close only after reconciliation, snapshot, and journal flush.
Market data, archive, and remaining infrastructure close last.

## Emergency-required state

A failed drain or an expired shutdown deadline transitions to
`EMERGENCY_REQUIRED`. The application deliberately does not choose between
continued risk handling and process termination.

The incident commander must explicitly choose one of these paths:

1. Keep the cell alive and disarmed, preserve public/private connectivity, issue
   scoped cancel/kill actions, reconcile until all outcomes are terminal, then
   start a new graceful shutdown with a newer generation.
2. If host or process survival is unsafe, invoke the venue-side emergency
   cancel procedure, capture the last journal/archive positions and process
   evidence, terminate the process, and require full startup reconciliation.

Never report `STOPPED` from the emergency path, never manually skip snapshot or
journal flush while the process remains healthy enough to complete them, and
never re-arm without a fresh restart/reconciliation after forced termination.

## Required evidence

Retain the command ID and result, operator-control journal position, active
configuration generation/hash, final lifecycle state, unresolved-order count,
reconciliation report, snapshot identity, final archive position, response-lane
drops, watchdog state, and shutdown deadline outcome for each operation.
