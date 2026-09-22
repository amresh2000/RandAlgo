# Phase 9 Bybit Gateway Certification Runbook

## Current status

The production-shaped offline boundary is implemented for the Bybit V5 inverse
`BTCUSD` profile. Unit/component fixtures run without credentials or network
access. Live testnet certification is intentionally pending; do not point the
order gateway at mainnet.

## Credentials to request

Request a dedicated Bybit testnet subaccount with:

- API key and secret delivered through the company-approved secret channel;
- contract-trading read/write permission and no withdrawal permission;
- the intended account mode and inverse `BTCUSD` access;
- the IP allowlist policy and test runner egress IP, if enforced;
- account fee tier, order/rate limits, and the authorized maximum test size;
- confirmation whether disconnect-cancel-all is enabled and its account/session scope.

Never commit credentials, paste them into fixtures, command history, logs, issue
trackers, or chat. The runtime owner must inject defensive byte copies from the
deployment secret provider and close each session-owned credential object during
shutdown so its stored bytes are zeroed.

## Required topology

Use the explicit testnet endpoints returned by `BybitAuthenticatedEndpoints.testnet()`:

- one independently authenticated V5 trade WebSocket;
- one independently authenticated V5 private WebSocket subscribed only to
  `order.inverse` and `execution.inverse`;
- signed HTTPS only for bounded reconciliation and metadata, never as the normal
  order path.

The two sockets require separate session coordinators and connection objects.
Keep the application disarmed until both authentication paths are LIVE, order
reconciliation is complete, and the market-data book is trustworthy. Phase 11
owns this lifecycle assembly.

## Certification sequence

1. Verify server-time offset and reject a deliberately invalid signature without
   exposing key material.
2. Authenticate both sockets, verify subscription acknowledgement, heartbeat,
   inactivity timeout, reconnect backoff, and a new session generation.
3. Submit the minimum authorized IOC order and prove that the trade response only
   records request acceptance while private order/execution messages drive state.
4. Exercise no-fill cancel, partial fill then cancel, full fill, venue reject,
   cancel/order race, duplicate fast/full execution, and conflicting duplicate.
5. Drop the trade connection before its response. Confirm the child becomes
   UNKNOWN, is not blindly resent, and resolves only from private or REST truth.
6. Reconnect during outstanding activity and prove stale-generation responses are
   rejected while reconciliation consumes every bounded cursor page.
7. Exercise documented rate limits and prove the adapter disarms/degrades without
   an unbounded retry loop.
8. Compare private fills, open/history REST results, account position/balance,
   fee rate, funding, and the slow payoff oracle for the admitted product.
9. Archive sanitized wire evidence, timings, account-mode facts, and test results;
   record hashes in the venue contract before changing the capability label.

## Pass criteria

- Every test order reaches an authoritative terminal state or remains explicitly
  UNKNOWN pending successful reconciliation.
- One venue execution identity produces exactly one fill; conflicting content
  faults the adapter.
- No secret appears in logs, exceptions, diagnostics, facts, or captured fixtures.
- Amount/price conversion exactly matches Bybit for every admitted order.
- Reconciliation and rate limits remain bounded, and no failure triggers blind
  retransmission.

Cancel-on-disconnect and mainnet assembly remain disabled until their observed
testnet semantics and operational ownership are approved.
