# Phase 10 Deribit Gateway Certification Runbook

## Current status

The production-shaped offline boundary is implemented for inverse
`BTC-PERPETUAL`. It has separate authenticated order and private WebSockets,
signed authentication and token refresh, monotonic JSON-RPC correlation, exact
native-unit encoding, authoritative private order/trade normalization, and
bounded complete-only reconciliation. Live testnet certification is still
pending. Never point this branch at mainnet.

## Credentials and account facts to request

Request a dedicated Deribit testnet subaccount with:

- client ID and client secret delivered through the approved secret channel;
- read and trade permissions, with withdrawal permission disabled;
- inverse `BTC-PERPETUAL` access and the authorized maximum test size;
- IP allowlist policy and the runner's egress IP, if enforced;
- account fee tier, portfolio margin mode, rate limits, and minimum order rules;
- confirmation of cancel-on-disconnect configuration and whether its scope is
  connection, subaccount, or account.

Never commit credentials or place them in fixtures, shell history, logs, issues,
or chat. Give each socket its own defensive credential/token owner and close it
on shutdown so retained byte arrays are zeroed.

## Required topology

Use `DeribitAuthenticatedEndpoints.testnet()`:

- one independently authenticated WebSocket for JSON-RPC order requests;
- one independently authenticated WebSocket for raw user order, trade, and
  portfolio subscriptions;
- bounded authenticated HTTPS only for recovery/reconciliation.

Phase 11 must keep trading disarmed until both sessions are LIVE, market data is
trustworthy, and startup reconciliation completes.

## Certification sequence

1. Verify server time, a known signature, and deliberate invalid authentication
   without exposing the client secret.
2. Authenticate both sockets. Observe token expiry/refresh, heartbeat
   `test_request`, inactivity timeout, reconnect backoff, and generation change.
3. Submit the minimum authorized IOC. Confirm response acceptance publishes no
   fill and raw private trade evidence publishes exactly one fill.
4. Exercise no-fill cancel, partial fill/cancel, full fill, venue reject,
   post-only rejection, cancel race, duplicate trade, and conflicting duplicate.
5. Pipeline at least two orders and force responses to arrive out of order;
   confirm request-ID correlation remains correct.
6. Drop the order socket after transport write but before response. Confirm
   WRITE_AMBIGUOUS, no retransmission, and resolution only from private/HTTP truth.
7. Drop the private socket with active orders. Confirm explicit disconnected/
   UNKNOWN evidence and complete open/history reconciliation after reconnect.
8. Exercise token-expired error `13009` and rate error `10028`; verify bounded
   backoff/degradation with no unbounded retry.
9. Compare open orders, history, trades, positions, account summary, instrument,
   fee/funding evidence, and the slow payoff oracle for every test order.
10. Archive sanitized wire evidence and latency stages with hashes, then update
    the capability contract before enabling Phase 11 arming.

## Pass criteria

- Every test order is terminal or explicitly UNKNOWN pending successful
  reconciliation.
- One currency-scoped trade identity creates one fill; conflicting content faults
  the adapter.
- Exact amount/price bytes agree with the intended inverse product economics.
- No credential or OAuth token appears in diagnostics, facts, or fixtures.
- Reconciliation consumes all bounded pages before publishing and never treats a
  partial result as complete.

Mainnet, cancel-on-disconnect, edit/mass-cancel routing, and additional products
remain disabled until their testnet evidence and operational ownership are
approved.
