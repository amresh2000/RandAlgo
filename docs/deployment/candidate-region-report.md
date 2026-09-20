# Candidate Region Report

**Status:** Measurement plan; placement not selected

## Documented topology

- Bybit currently identifies AWS Singapore availability zones.
- Deribit identifies its primary infrastructure in London, Equinix LD4.
- Deribit also documents selected low-latency/private feed arrangements in
  London and Tokyo, but those are not assumed available to this project.

These statements seed candidate selection only. DNS, front doors, routing,
account endpoints, and matching-engine paths must be measured at execution time.

## Candidate regions

| Region | Reason to measure | Principal risk |
|---|---|---|
| AWS Singapore | Documented proximity to Bybit | Wide-area Deribit path |
| AWS London | Proximity to Deribit/LD4 | Wide-area Bybit path |
| Intermediate Asia/Europe candidate | Possible balanced public paths | Not local to either venue |

## Required experiment

From production-equivalent instances in each candidate region, measure:

- DNS and TCP/TLS/WebSocket establishment;
- public and private heartbeat RTT, jitter, disconnects, and update age;
- order-write to acknowledgement/fill where test credentials permit;
- complete application-stage tails when the Java path exists;
- packet loss/reordering and route changes;
- time synchronization, CPU topology, storage, and operational support.

Store region, instance type, kernel, route, endpoint, account/feed profile,
artifact/configuration hash, and UTC interval with every result.

## Decision rule

Select the single-cell region that minimizes expected risk-adjusted execution
loss, including fill-to-hedge tails and opportunity duration. Do not choose the
arithmetic midpoint of two pings. No region is selected until comparable runs
exist from at least Singapore and London.

## Current blockers

Candidate cloud accounts/instance types, test trading credentials, and feed
entitlements have not been supplied. Public Internet measurements from the
developer workstation are diagnostic only and cannot choose production hosting.

