# Phase 12 End-to-End Testnet Certification Runbook

## Status and safety boundary

The certification harness and fail-closed exit gate are implemented. Live
Bybit and Deribit testnet evidence, the 24-hour soak, and the 7-day soak have not
yet been executed. Phase 12 is therefore **not certified**.

Only dedicated, funded nonproduction subaccounts may be used. Withdrawal
permission must be disabled. The authorized maximum order size, total loss,
instruments, test window, egress IPs, account mode, fee tier, and responsible
operator must be approved before order transmission. Production endpoints and
credentials are prohibited.

The harness stores hashes and bounded status metadata, not credentials, tokens,
raw authenticated messages, account identifiers, or free-form notes. Keep raw
sanitized evidence outside the repository in the company-approved evidence
store.

## Inputs

Obtain the account details listed in the Phase 9 and Phase 10 runbooks. Inject
secrets from the approved secret provider into the process; never put them in
the evidence JSON, command-line arguments, shell history, logs, heap dumps, JFR
settings, or repository files.

Record these non-secret approvals separately:

- exact Bybit and Deribit testnet account modes and admitted instruments;
- minimum and maximum test quantity and aggregate loss limit;
- expected maker/taker fees, funding treatment, and rate tiers;
- cancel-on-disconnect scope and whether it is enabled;
- operator, reviewer, test window, and emergency-stop owner;
- immutable application revision and signed configuration generation.

## Prepare evidence

From the repository root, create a new evidence file outside the repository:

```bash
python3 tools/phase12_certification.py init \
  --revision 4422a24 \
  --output /approved/evidence/location/phase-12-evidence.json
```

Replace the example revision with the exact commit under test. The tool refuses
to overwrite an existing file. Confirm the checked-in offline fixtures before
connecting:

```bash
python3 tools/phase12_certification.py verify-fixtures --root .
```

Run the Java 25 offline gate first:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o -T1C clean verify
```

## Mandatory execution order

1. Start disarmed. Verify signed configuration, exact live metadata, both public
   books, both order/private sessions, journal/archive capacity, rate capacity,
   clock health, and complete startup reconciliation.
2. Prove manual kill and emergency cancel paths at zero exposure.
3. Execute each venue scenario at the minimum approved size: accepted,
   rejected, partial fill, full fill, IOC residual, cancel race, duplicate,
   lost acknowledgement, private disconnect, public gap, and rate limit. Execute
   token expiry/refresh on Deribit.
4. Execute cell scenarios: process kill, archive outage, restart, manual kill,
   two fresh books with excessive receive-time skew, urgent-queue latency, and
   order-agent latency.
5. For queue/order latency, record `HEALTHY -> DEGRADED` or `UNSAFE`, prove that
   new initiation is suppressed, and prove hedge/cancel/unwind traffic remains
   available. Recovery requires the configured healthy hysteresis window and
   any required reconciliation.
6. After every scenario, reconcile and independently verify all seven state
   dimensions: positions, balances, fills, fees, open orders, reservations, and
   journal. A scenario cannot be `PASSED` if any dimension is absent.
7. Hash every sanitized artifact with:

   ```bash
   python3 tools/phase12_certification.py hash --file /approved/evidence/location/artifact
   ```

   Put only the resulting lowercase SHA-256 in the evidence file.
8. Run an uninterrupted 24-hour soak, review it, then run an uninterrupted
   7-day soak. Each requires allocation/JFR, queue, CPU, memory, reconnect, and
   end-to-end latency reports. A shorter run cannot satisfy a longer gate.
9. Sanitize representative live wire captures, review them for secrets and
   identifiers, freeze them with a SHA-256 manifest, and obtain approval for
   each venue. Existing documentation examples are not live evidence.
10. Record all findings. Any unresolved severity-1 or severity-2 finding blocks
    certification.

## Scenario-specific proof

- A lost acknowledgement or ambiguous write must become `UNKNOWN`; no blind
  retransmission is allowed, and reservation release waits for authoritative
  private/query reconciliation.
- Duplicate and reordered events must not duplicate fills, position changes,
  P&L, or reservation release. Conflicting duplicates must fail safe.
- Partial initiating fills must hedge only the confirmed fill increment with
  conservative native-unit rounding.
- A public gap or disconnect must revoke book trust before another initiation.
- A private disconnect must stop venue/account initiation and reserve possible
  fills until complete reconciliation.
- Process kill/restart must replay and reconcile both venues and remain disarmed
  until books, open/unknown exposure, balances, positions, and journal state are
  resolved. Re-arming is an explicit operator action.
- Archive high water/outage must stop initiation while preserving capacity for
  fills, unknowns, faults, cancels, hedges, and operator actions.

## Evaluate and approve

Validation recalculates the result and exits nonzero for `INCOMPLETE` or
`FAILED`:

```bash
python3 tools/phase12_certification.py validate \
  --evidence /approved/evidence/location/phase-12-evidence.json \
  --output /approved/evidence/location/phase-12-report.json
```

The output is `CERTIFIED` only when every applicable scenario and required
acceptance criterion passed with evidence, both soak gates passed, both venue
fixture sets were approved, testnet limitations were acknowledged, and no open
severity-1/2 finding remains. Release automation must also enforce the Java
`CertificationGate`; its unit suite proves the same fail-closed contract used by
the command-line validator.

## Testnet limitations

- Testnet liquidity, matching priority, spreads, fill probability, market
  impact, traffic, rate tiers, maintenance behavior, and latency do not represent
  mainnet.
- Testnet account mode, margin behavior, fee schedules, funding, entitlements,
  instrument availability, and cancel-on-disconnect scope may differ from the
  intended production subaccounts.
- Injected disconnect, gap, archive, queue, and process faults prove local
  fail-closed behavior; they do not prove every venue or infrastructure outage
  mode.
- A laptop soak demonstrates functional stability only. It cannot satisfy the
  target-host end-to-end latency, CPU isolation, GC, network path, or regional
  reliability gates.
- Passing Phase 12 does not authorize mainnet transmission. Phase 13 production
  shadow evidence and later staged canary approvals remain mandatory.

These limitations must remain attached to the signed certification report and
must not be extrapolated into a mainnet performance or economic claim.
