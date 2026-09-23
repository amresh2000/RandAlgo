# Phase 12 testnet certification implementation plan

## Outcome

Create the fail-closed certification layer that turns Phase 9-11 venue and cell
behavior into reviewable evidence. The implementation must distinguish an
implemented harness from a completed live certification: no missing scenario,
acceptance criterion, soak, fixture approval, or severe finding may be hidden by
an aggregate PASS result.

## Scope

1. Add a cold-path certification domain in `basis-app` with the complete Phase
   12 scenario matrix and the Phase 12 acceptance-criterion set.
2. Require explicit verification of positions, balances, fills, fees, open
   orders, reservations, and journal state after every applicable scenario.
3. Model 24-hour and 7-day soak evidence, including allocation/JFR, queue, CPU,
   memory, reconnect, and latency reports.
4. Model frozen wire-fixture evidence and unresolved severity findings.
5. Implement a fail-closed evaluator and deterministic JSON report writer.
6. Add tests proving matrix completeness, missing-evidence rejection, severe
   finding rejection, soak/fixture rejection, and credential-safe reporting.
7. Add a Phase 12 runbook, evidence-template generator, and fixture hash
   verifier. Update venue contracts without claiming unexecuted live evidence.

## Validation

- Java 25 module tests for `basis-app` and dependencies.
- Python unit tests for certification tooling.
- Full offline Maven `clean verify`.
- Fixture hash verification against repository bytes.

## External execution still required

The implementation can make the certification repeatable and auditable, but it
cannot manufacture live venue outcomes or elapsed soak time. Dedicated Bybit
and Deribit testnet credentials, funded nonproduction accounts, explicit order
authorization, 24 hours, and then 7 days are required to satisfy the live exit
gate. Until imported evidence satisfies every gate, the evaluator must return
`INCOMPLETE` or `FAILED`, never `CERTIFIED`.
