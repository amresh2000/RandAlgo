# Feature: Phase 8 journal, snapshots, replay, and recovery

This plan implements `docs/implementation-plan.md` Phase 8 on top of the Phase 7 deterministic command/fact path. It preserves the architecture decisions in `docs/architecture.md` and `docs/component-design.md`: SBE is the durable contract, Aeron Archive is the append-only local journal, core state remains single-writer and bounded, PostgreSQL is a rebuildable cold projection, and every restart remains disarmed until replay plus authoritative venue and book reconciliation complete.

## Feature Description

Build the durable recovery boundary for the execution cell. Every safety-relevant normalized input, decision, command, order fact, state transition, operator action, reconciliation result, and journal fault is encoded as a versioned SBE event, admitted through a bounded nonblocking journal ingress, recorded by Aeron Archive, and replayable into fresh bounded core state. Periodic versioned/checksummed snapshots accelerate recovery without replacing the journal. A restart coordinator enforces the declared disarmed recovery sequence, and a downstream PostgreSQL projector can be deleted and rebuilt from retained durable data.

## User Story

As the execution-cell owner, I want a killed process to reconstruct its local safety state and reconcile any unrecorded crash tail with both venues so that it cannot resume exposure from incomplete, stale, or contradictory state.

## Problem Statement

Phase 7 proves deterministic behavior only within one process lifetime. `basis-journal` contains no implementation, core tables lack controlled exact-restore APIs, the existing SBE schema does not carry every field needed to reconstruct reservations/orders/groups and execution dedupe state, and there is no archive health policy, snapshot format, replay dispatcher, restart coordinator, retention guard, or PostgreSQL projection. A crash therefore loses local reservations, order ambiguity, kill generations, and the evidence required to prove a safe restart.

## Solution Statement

Extend the existing SBE schema append-only to version 2 with full-after-state durable facts for reservation transitions, normalized order facts, kill state, and journal faults. Keep event classification outside wire payload: template/event type maps deterministically to `CRITICAL`, `IMPORTANT`, or `LOSSY`. Core/application seams encode into a fixed-capacity SPSC journal ingress. Normal initiation/important traffic is rejected at a configured high-water boundary that preserves a byte-counted critical reserve; terminal and risk-reducing facts can consume the reserve. Rejection or archive lag degrades journal health and disarms initiation without blocking the core.

Use the pinned Aeron `1.53.2` APIs. For v1, launch an embedded `ArchivingMediaDriver` with explicit directories and dedicated archive threading, start a local IPC recording with `AeronArchive.startRecording(..., SourceLocation.LOCAL)`, publish through one `ExclusivePublication`, and observe the archive `RecordingPos` counter. Keep construction behind `ArchiveRuntime`/`EventJournal` interfaces so a separately launched archive can be attached later without changing producers or replay.

Snapshots use a repository-owned binary format rather than Java serialization: fixed header, format/schema versions, build/config identity, archive recording ID/position, capture epoch, capacities, payload length, and SHA-256 checksum. The core owner copies a bounded canonical image into a preallocated buffer; a cold writer writes a same-directory temporary file, forces it, verifies it, and atomically renames it. Loading validates the complete file before mutating a fresh recovery state. Absolute monotonic timestamps are never treated as portable across JVM lifetimes; pending/unknown exposure restores conservatively and remains reconciliation-gated.

Replay starts at the snapshot's recorded journal position, validates SBE schema/template/version and monotonic event sequence, and applies idempotent full-after-state facts to fresh tables. The coordinator progresses only through `BOOT -> SNAPSHOT_LOADED -> JOURNAL_REPLAYED -> PRIVATE_CONNECTED -> ORDERS_RECONCILED -> POSITIONS_RECONCILED -> BOOKS_TRUSTED -> WARMED -> DISARMED_READY`. V1 never auto-arms. Venue differences append compensating reconciliation facts; archived history is never edited.

The PostgreSQL projector runs behind replay, not the core. It batches raw event bytes plus indexed metadata into an append-only table keyed by `(recording_id, fragment_position)` using `INSERT ... ON CONFLICT DO NOTHING`. Its checkpoint advances in the same database transaction. Connection, schema, or availability failures only stop projection and raise cold health; they never feed back into core state or journal publication.

## Recommended Direction

Use an embedded Media Driver + Archive for v1 and integration tests because the declared deployment is one execution-cell JVM. Preserve an external-archive connection seam, but do not build replication or a second deployment topology in this phase. This is the smallest design that proves the exact production record/replay path without committing future process placement.

Use one ordered lossless event stream with a reserve-aware ingress rather than separate critical and normal archive streams. A single stream retains total order; the ingress high-water boundary reserves capacity for the worst-case terminal/risk-reducing tail after initiation stops.

Use pgJDBC `42.7.13`, the current Maven Central release at planning time, via standard `DataSource`/JDBC on a cold projector thread. Keep credentials and URLs out of repository configuration. The projector schema should retain raw SBE bytes so new projections can be rebuilt after decoder/schema evolution.

## Out of Scope / Non-Goals

- No authenticated Bybit/Deribit private connections or HTTP reconciliation implementation; Phases 9-10 implement the real `VenueRecoveryPort` adapters.
- No production operator HTTP/CLI, complete application lifecycle assembly, or deployment service definitions; Phase 11 owns those.
- No automatic arm after recovery. V1 terminates at `DISARMED_READY`; an explicit later operator transition is mandatory.
- No Aeron Archive replication, clustered storage, remote disaster recovery, or cross-host failover.
- No synchronous fsync on the trading path and no claim that the asynchronous crash tail always survives host/storage failure. Venue reconciliation resolves that tail.
- No snapshotting of order books. Both books must reconnect, receive authoritative images, and pass freshness warm-up after restart.
- No PostgreSQL authority over trading state and no core dependency on JDBC.
- No history edits. Corrections are new compensating facts.
- No raw private-frame retention in the durable event journal; existing bounded/redacted capture remains a separate certification facility.

## Feature Metadata

**Feature Type**: New capability / safety infrastructure  
**Estimated Complexity**: Very high  
**Primary Systems Affected**: `basis-protocol`, `basis-core`, `basis-journal`, `basis-sim`, `basis-app`, root dependency management  
**Dependencies**: Phase 2 SBE schema, Phase 6 risk/OEMS state, Phase 7 normalized facts and deterministic recovery scenarios, Aeron `1.53.2`, Agrona `2.6.1`, SBE `1.40.2`, pgJDBC `42.7.13`

## Related Work

**Implements**: `docs/implementation-plan.md` Phase 8 / atomic task 13  
**Satisfies**: durable portion of AC14 and the Phase 8 exit gate  
**Architecture source**: `docs/architecture.md` sections 10-11 and `docs/component-design.md` section 15

**Back-references**:

- `.claude/plans/phase-7-deterministic-simulator.md` — normalized order facts, deterministic scheduler, failure scenarios, canonical state visitation.
- `.claude/plans/phase-6-risk-execution.md` — reservations, risk ledger, order/group state, UNKNOWN and reconciliation invariants.
- `.claude/plans/phase-1-engineering-foundation.md` — pinned Java/Maven/Aeron/SBE versions and replay/chaos test profiles.

**Forward-references**:

- Phases 9-10 provide actual venue reconciliation data through the recovery ports defined here.
- Phase 11 wires archive directories, lifecycle, credentials, operator controls, and explicit arming.
- Phase 12 certifies kill/restart/reconcile behavior against testnet venue truth.

---

## CONTEXT REFERENCES

### Relevant Codebase Files — read before implementation

- `docs/architecture.md:503` — nonblocking Aeron/SBE boundary and backpressure policy.
- `docs/architecture.md:522` — authoritative restart sequence and crash-tail compensation rule.
- `docs/component-design.md:675` — event classes, critical reserve, asynchronous durability limitation, snapshot contents, and no-auto-arm lifecycle.
- `docs/implementation-plan.md:640` — Phase 8 tasks and measurable exit gate.
- `pom.xml:29` — pinned Aeron/Agrona/SBE versions and test profiles; add pgJDBC dependency management here.
- `basis-protocol/src/main/resources/sbe/basis-messages.xml:1` — schema 1001 version 1; evolve append-only to version 2.
- `basis-protocol/src/main/resources/sbe/basis-messages.xml:283` — existing risk/order/group/fill/position/control/snapshot/reconciliation templates.
- `basis-protocol/src/test/java/com/penguinsecure/basis/protocol/ProtocolCompatibilityTest.java` — golden-frame and previous-version decoder pattern.
- `basis-journal/pom.xml:1` — empty module currently depending only on protocol; add core, Aeron, Agrona, and pgJDBC.
- `basis-journal/src/main/java/com/penguinsecure/basis/journal/package-info.java:1` — WARM journal-agent ownership boundary.
- `basis-core/src/main/java/com/penguinsecure/basis/core/oems/ChildOrderTable.java:1` — local-ID and execution-ID indexes must be captured and rebuilt exactly.
- `basis-core/src/main/java/com/penguinsecure/basis/core/oems/ExecutionGroupTable.java:1` — bounded group state and generations.
- `basis-core/src/main/java/com/penguinsecure/basis/core/risk/RiskReservationTable.java:1` — reservations retain token-bucket references; recovery must rebind configured buckets safely.
- `basis-core/src/main/java/com/penguinsecure/basis/core/risk/StrategyRiskLedger.java:1` — canonical positions/exposure/pending state.
- `basis-core/src/main/java/com/penguinsecure/basis/core/risk/PartitionedTokenBucket.java:1` — rate partitions and reserved hedge capacity require snapshot/restore support.
- `basis-core/src/main/java/com/penguinsecure/basis/core/risk/KillHierarchy.java:1` — every scope generation and killed bit must survive restart.
- `basis-core/src/main/java/com/penguinsecure/basis/core/oems/OemsFactProcessor.java:1` — authoritative reconciliation and compensating facts reuse this path.
- `basis-sim/src/main/java/com/penguinsecure/basis/sim/digest/CanonicalSimulationDigest.java:1` — stable canonical state ordering used for before/after recovery comparison.
- `basis-sim/src/main/java/com/penguinsecure/basis/sim/runner/SimulationRunner.java:1` — deterministic kill/crash-tail harness to extend with durable restart.
- `basis-app/src/main/java/com/penguinsecure/basis/app/core/CoreAgent.java:1` — health-first duty-cycle precedent; final production composition remains Phase 11.
- `basis-app/src/test/java/com/penguinsecure/basis/architecture/ArchitectureRulesTest.java:1` — core must remain independent of Aeron/JDBC/persistence.

### Relevant external documentation

- [Aeron 1.53.2 recorded publisher](https://github.com/aeron-io/aeron/blob/1.53.2/aeron-samples/src/main/java/io/aeron/samples/archive/RecordedBasicPublisher.java)
  - Why: exact `startRecording`, publication, `RecordingPos`, and archive-lag pattern for the pinned release.
- [Aeron 1.53.2 embedded recording sample](https://github.com/aeron-io/aeron/blob/1.53.2/aeron-samples/src/main/java/io/aeron/samples/archive/EmbeddedRecordingThroughput.java)
  - Why: exact `ArchivingMediaDriver.launch(MediaDriver.Context, Archive.Context)` construction.
- [Aeron 1.53.2 replay subscriber](https://github.com/aeron-io/aeron/blob/1.53.2/aeron-samples/src/main/java/io/aeron/samples/archive/ReplayedBasicSubscriber.java)
  - Why: recording lookup, `startReplay`, replay session filtering, and subscription pattern.
- [Aeron Java programming guide](https://github.com/aeron-io/aeron/wiki/Java-Programming-Guide)
  - Why: publication result codes, IPC channels, threading, and controlled nonblocking operation.
- [pgJDBC usage guide](https://github.com/pgjdbc/pgjdbc/blob/master/docs/content/documentation/use.md)
  - Why: connection/session configuration and standard JDBC behavior.
- [pgJDBC Maven Central metadata](https://repo.maven.apache.org/maven2/org/postgresql/postgresql/maven-metadata.xml)
  - Why: pins `42.7.13` rather than an unversioned/latest dependency.

### Patterns to follow

**Bounded hot/warm paths:** fixed primitive arrays and caller-owned mutable views; no unbounded collection growth, blocking waits, sleeps, futures, or retries on the core/journal duty cycles.

**Protocol evolution:** append fields with `sinceVersion`, never renumber existing template/field/enum IDs, increment schema version, retain v1 golden frames, and add v2 golden frames.

**Failure handling:** explicit status enums and health snapshots. Aeron `BACK_PRESSURED`, `ADMIN_ACTION`, `NOT_CONNECTED`, `CLOSED`, and `MAX_POSITION_EXCEEDED` are distinct outcomes; none is silently retried on the caller's thread.

**State restoration:** decode into a fresh bounded state aggregate, validate all capacities, generations, references, arithmetic, indexes, checksums, and cross-table invariants, then publish the aggregate as one recovery result. Never partially mutate live state from an unverified snapshot.

**Files:** same-directory temporary file, restrictive permissions, `FileChannel.force(true)`, read-back checksum, atomic rename, then directory force where supported. A failed write never replaces the last good snapshot.

**PostgreSQL:** cold-only standard JDBC, prepared batches, explicit transactions, idempotent primary keys, bounded retry state advanced by the projector agent. Never store secrets in source or log connection URLs containing credentials.

---

## NEW FILES / PACKAGES TO CREATE

- `basis-core/.../recovery/CoreRecoveryState.java` — owns fresh bounded tables during snapshot load/replay.
- `basis-core/.../recovery/CoreStateRestorer.java` — validated exact restore/rebuild of indexes and cross-references.
- `basis-core/.../recovery/RecoveryValidationStatus.java` and `package-info.java` — primitive recovery outcomes; HOT/WARM classification as appropriate.
- `basis-journal/.../config/JournalConfiguration.java` — explicit directories, channels, stream IDs, capacities, reserve, lag/watermarks, snapshot/retention/projector settings.
- `basis-journal/.../ingress/{JournalEventClass,JournalOfferStatus,JournalIngress,JournalHealth}.java` — bounded reserve-aware FIFO and independent health.
- `basis-journal/.../codec/{JournalEventEncoder,JournalEventDecoder,JournalTemplateClassifications}.java` — generated-SBE facade and replay dispatch.
- `basis-journal/.../archive/{ArchiveRuntime,AeronArchiveRuntime,EventJournal,AeronEventJournal,ArchiveJournalAgent}.java` — lifecycle, recording, publication, lag, and controlled shutdown.
- `basis-journal/.../snapshot/{CoreSnapshotCodec,CoreSnapshotStore,FileCoreSnapshotStore,SnapshotDescriptor,SnapshotLoadResult}.java` — canonical binary format and atomic persistence.
- `basis-journal/.../replay/{JournalReplay,ArchiveJournalReplay,ReplayEventHandler,ReplayReport}.java` — recording discovery and bounded fragment replay.
- `basis-journal/.../recovery/{RecoveryCoordinator,RecoveryStage,RecoveryStatus,VenueRecoveryPort,BookRecoveryPort,RecoveryInvariantChecker}.java` — fail-closed restart state machine.
- `basis-journal/.../projection/{EventProjector,ProjectionStore,JdbcProjectionStore,ProjectionCheckpoint}.java` — downstream idempotent projection.
- `basis-journal/src/main/resources/db/phase-8-projection.sql` — append-only event/checkpoint schema.
- Focused tests mirroring each package plus replay/chaos integration tests under `basis-journal/src/test/java` and durable restart acceptance tests under `basis-sim/src/test/java`.

Exact file splitting may be reduced where a type is private and single-use, but package ownership and dependency direction must remain as listed.

---

## IMPLEMENTATION PLAN

### Phase 1: Durable protocol and complete state seams

- Increment SBE schema version to 2 without changing existing IDs.
- Add full-after-state templates for reservation state, normalized order facts/write ambiguity, kill state, and journal fault/health. Add only the missing versioned fields required to reconstruct existing order/group/position/reconciliation facts.
- Define stable primitive codes for provenance, lifecycle, reservation/group states, fault reasons, and recovery actions; do not persist Java enum ordinals.
- Add v2 golden frames and prove the v2 decoder reads v1 while the current encoder remains pinned.
- Add deterministic visitation and controlled restore methods for child orders including execution-dedupe entries, groups, ledger, reservations, rate buckets, and kill hierarchy.
- Restore into empty tables only. Rebuild free lists/hash indexes from validated records rather than serializing internal array/hash layout.
- Rebind restored reservations to freshly configured `PartitionedTokenBucket` instances and reconstruct their reserved claims. If configuration generation/capacity mismatches, fail recovery.
- Never carry absolute monotonic deadlines across processes. Restore nonterminal order/group/reservation exposure as UNKNOWN/reconciliation-gated; use capture epoch only to conservatively expire ancillary timers.

### Phase 2: Reserve-aware journal ingress and event encoding

- Implement one SPSC lossless FIFO containing complete encoded SBE frames and a separate lossy telemetry lane.
- Maintain `normalLimitBytes = capacityBytes - criticalReserveBytes`. `IMPORTANT`/new-exposure events cannot cross the normal limit; `CRITICAL` terminal/reducing events may consume the reserve. `LOSSY` events never consume lossless capacity.
- Calculate minimum critical reserve from configured maximum outstanding children/groups and the maximum encoded terminal sequence per child; reject startup configuration below that bound.
- Preserve admission order in the single lossless FIFO. Assign a monotonic journal event sequence at successful admission, never before, so rejected events create no phantom sequence.
- Encode with generated SBE codecs into claimed fixed-capacity records. Reject oversize, invalid class/template combinations, stale producer sequence, or capacity exhaustion explicitly.
- Publish a cache-line-isolated journal health snapshot containing ingress occupancy, reserve remaining, failed offers by class, publication result, archive recording lag, disk watermark state, and last progress.
- On normal high-water or archive lag, signal fail-closed initiation disarm while allowing critical events. On exhausted critical reserve or closed/max-position publication, escalate global fault/kill.

### Phase 3: Aeron Archive runtime and journal agent

- Add `aeron-all` to `basis-journal`; configure Media Driver directory and Archive directory separately and require explicit non-root paths.
- Launch `ArchivingMediaDriver` with explicit threading/idle strategies, IPC term length, archive segment file length, catalog/mark paths, error handlers, checksum policy, and `dirDeleteOnStart(false)` outside tests.
- Connect one `AeronArchive` client, start local IPC recording, create one `ExclusivePublication`, and discover the recording via `RecordingPos.findCounterIdBySession` scoped by archive ID.
- Implement archive startup as an agent state machine with deadlines. No core thread waits for recording/publication connection.
- Drain lossless ingress before lossy telemetry. Use bounded `tryClaim`/`offer`; map every negative publication result to a stable status and update health.
- Track admitted position, publication position, and recording position independently. A snapshot is eligible only after the archive recording position covers its journal boundary.
- Poll archive error responses and recording liveness. Recording disappearance, storage watermark, catalog error, or no progress within bounds disarms initiation.
- Implement controlled stop: stop admission, drain critical facts to a declared deadline, wait for recording position to reach publication position, stop recording, and close in reverse dependency order.
- Implement retention only for stopped segments strictly before both the latest verified compatible snapshot and projector checkpoint. Never purge the active tail or the only compatible recovery base.

### Phase 4: Versioned snapshots and atomic store

- Define a fixed snapshot header with magic, format version, SBE schema ID/version, build/config/catalog generations, snapshot ID, recording ID/position, capture epoch, capacities, payload length, and checksum algorithm/bytes.
- Encode all ledger fields; reservation slots/generations/states and rate claims; group slots/generations/references; child slots/generations/state/identity and execution dedupe identities; kill scopes/generations; session/config generations; next journal sequence; and replay counters.
- Do not encode live book depth/trust as restart authority. The recovery coordinator always requires new book images/warm-up.
- Validate bounds and cross-links while encoding. Refuse a snapshot if any active child references an invalid group, any group references an invalid reservation, exposure arithmetic disagrees, or journal position is not durably covered.
- Write to `<snapshot>.tmp` in the final directory, force file contents, read back and verify checksum/header, atomically move to final name, and force the directory where supported. Retain the previous verified snapshot until the new one is published.
- Loader scans only well-formed final filenames, newest first, and chooses the latest compatible verified snapshot. It skips corrupt/truncated/incompatible candidates with explicit diagnostics and never partially loads one.
- Decode into `CoreRecoveryState`, validate again, and expose a canonical digest for comparison with pre-kill state.

### Phase 5: Replay and fail-closed recovery coordinator

- Locate the configured recording, validate that the snapshot position is inside its retained range, and start replay from that exact position on a separate replay stream/session.
- Reassemble fragmented SBE messages, validate schema ID/template ID/acting version/block length, and reject unknown critical templates. Unknown explicitly-lossy future templates may be skipped with counters.
- Enforce strictly increasing admitted event sequence and idempotent event identity. Exact duplicate `(recordingId, fragmentPosition)` is harmless; same event key with different bytes is a fatal conflict.
- Apply full-after-state events to the fresh recovery aggregate using stable identities/generations. Replay must not emit live outbound order commands.
- Extend Phase 7 with kill-at-every-transition and crash-tail scenarios: snapshot, record prefix, kill, lose an admitted-but-not-recorded tail, load/replay, inject venue truth, emit compensating facts, and compare canonical safe state.
- Implement the recovery stages exactly as documented. Every transition requires its predecessor and explicit evidence; failures remain at the current stage or move to `FAILED`, never skip ahead.
- `VenueRecoveryPort` accepts bounded open-order, fill, position, balance, and session results from future adapters. Resolve by deterministic local/venue IDs through `OemsFactProcessor` and append reconciliation/compensation facts.
- `BookRecoveryPort` requires both configured venue books to be new-session `TRUSTED` and fresh through the full warm-up window.
- End only at `DISARMED_READY`. Do not expose an `ARMED` transition from Phase 8.

### Phase 6: PostgreSQL projection and rebuild

**Independent of:** snapshot file encoding after Phase 1 establishes the event schema.

- Pin pgJDBC `42.7.13` in root dependency management and add it only to `basis-journal`.
- Add an append-only table containing recording ID, fragment position, event sequence, template/schema versions, event type, producer/correlation identity, selected indexed route fields, and raw SBE bytes. Primary key is `(recording_id, fragment_position)`.
- Add a singleton projector checkpoint table keyed by projection name. Insert a bounded batch and advance its checkpoint in one transaction using `ON CONFLICT DO NOTHING` for events.
- Projector reads from archive replay, not the core ingress. It can stop/retry without retaining mutable trading references or backpressuring the journal.
- Validate payload size and decoder compatibility before commit. Preserve unknown future raw events even when no typed columns are available.
- Add a rebuild command/service that truncates only projector-owned tables after explicit operator/test authorization, replays from the earliest retained position/snapshot marker, and proves the same row count/key/digest.
- Redact JDBC credentials from errors. Accept a constructed `DataSource`; Phase 11 owns environment/config parsing.

### Phase 7: Failure certification and operational evidence

- Add unit tests for classification, reserve sizing, FIFO order, health transitions, publication result mapping, codecs, restore validation, checksum/truncation, stage transitions, and projector idempotency.
- Add `@Tag("replay")` embedded-Archive tests using per-test temporary Media Driver/Archive/snapshot directories.
- Add `@Tag("chaos")` kill points before/after ingress commit, publication, recording position advance, snapshot force/rename, replay apply, compensation append, and projector transaction commit.
- Test archive unavailable, disconnected, backpressured, closed, max position, recording stopped, lag watermark, disk watermark, corrupt catalog response, and insufficient critical reserve.
- Test corrupt/truncated/incompatible snapshots, invalid cross-references, duplicate replay, conflicting duplicate, retained-range gap, and schema downgrade/upgrade.
- Test venue/journal disagreement where the venue has an extra fill: restore UNKNOWN exposure, append the authoritative fill and compensation, converge positions/reservations, and remain disarmed.
- Prove PostgreSQL deletion/rebuild against a disposable PostgreSQL instance and compare canonical projected keys/digest.
- Document asynchronous durability limitation, sizing formula, directory ownership/permissions, storage alarms, retention preconditions, recovery runbook, and manual arm boundary.

---

## STEP-BY-STEP TASKS

### 1. UPDATE root and journal dependencies

- **IMPLEMENT**: add `postgresql.version=42.7.13`; add `aeron-all`, `agrona`, `basis-core`, and pgJDBC to `basis-journal` with no dependency from core back to journal.
- **VALIDATE**: `./mvnw -pl basis-journal -am dependency:tree`
- **SATISFIES**: pinned/runtime foundation.

### 2. UPDATE `basis-messages.xml` and protocol goldens

- **IMPLEMENT**: schema v2 durable templates/fields with stable codes and previous-version compatibility.
- **GOTCHA**: never reuse IDs or persist Java enum ordinals.
- **VALIDATE**: `./mvnw -o -pl basis-protocol test`
- **SATISFIES**: complete replay evidence.

### 3. UPDATE core tables with controlled snapshot/restore seams

- **IMPLEMENT**: complete visitation, exact-empty restore, index/free-list rebuild, rate-bucket rebinding, and invariant validation.
- **GOTCHA**: no Aeron, file, SBE, JDBC, or journal imports in core.
- **VALIDATE**: `./mvnw -o -pl basis-core test`
- **SATISFIES**: safe reconstructable state.

### 4. CREATE journal ingress, encoder, and health

- **IMPLEMENT**: reserve-aware fixed-capacity FIFO, classification table, admission sequence, SBE encoding, status/health contracts.
- **VALIDATE**: `./mvnw -o -pl basis-journal -am test`
- **SATISFIES**: journal backpressure and critical reserve exit gate.

### 5. CREATE embedded archive runtime and `ArchiveJournalAgent`

- **IMPLEMENT**: explicit contexts/directories, recording/publication state machine, lag monitoring, bounded drain, retention guards, controlled close.
- **PATTERN**: official Aeron 1.53.2 samples linked above.
- **VALIDATE**: `./mvnw -o -Preplay-tests -pl basis-journal -am verify`
- **SATISFIES**: durable append/replay path.

### 6. CREATE snapshot codec/store and core recovery aggregate

- **IMPLEMENT**: canonical bounded binary image, SHA-256, force/read-back/atomic rename, newest-compatible loader, digest.
- **VALIDATE**: `./mvnw -o -pl basis-core,basis-journal -am test`
- **SATISFIES**: compatible fast restart and corrupt snapshot safety.

### 7. CREATE archive replay dispatcher

- **IMPLEMENT**: recording discovery/range checks, session-filtered replay, fragment assembly, SBE dispatch, duplicate/conflict handling, report.
- **VALIDATE**: `./mvnw -o -Preplay-tests -pl basis-journal,basis-sim -am verify`
- **SATISFIES**: deterministic event reconstruction.

### 8. CREATE recovery coordinator and compensating reconciliation path

- **IMPLEMENT**: documented stages, future venue/book ports, UNKNOWN retention, compensation append, no auto-arm.
- **VALIDATE**: `./mvnw -o -Preplay-tests -pl basis-journal,basis-sim -am verify`
- **SATISFIES**: durable AC14.

### 9. CREATE PostgreSQL projector and schema

- **IMPLEMENT**: raw append-only event table, indexed metadata, transactional idempotent batch/checkpoint, bounded retry, rebuild.
- **GOTCHA**: projector errors never alter journal/core state; credentials never enter logs.
- **VALIDATE**: `./mvnw -Pintegration-tests -pl basis-journal -am verify -Dbasis.test.postgres.url=jdbc:postgresql://localhost:5432/basis_test`
- **SATISFIES**: rebuildable cold projection exit gate.

### 10. ADD kill/crash/replay/chaos acceptance matrix

- **IMPLEMENT**: every state transition and persistence boundary, crash-tail venue disagreement, reserve exhaustion, disk/archive faults, deterministic digest comparison.
- **VALIDATE**: `./mvnw -o -Pchaos-tests -pl basis-journal,basis-sim -am verify`
- **SATISFIES**: all Phase 8 exit gates.

### 11. UPDATE operational documentation

- **IMPLEMENT**: storage sizing, reserve formula, permissions, alarms, retention, asynchronous durability limitation, recovery stages, projector rebuild, explicit manual-arm boundary.
- **VALIDATE**: `./mvnw -o spotless:check`
- **SATISFIES**: safe deployment handoff.

---

## TESTING STRATEGY

### Unit tests

- SBE v1/v2 compatibility and stable numeric codes.
- Every journal offer/publication status and class/template mapping.
- Reserve formula at zero, exact boundary, one-byte-over, and maximum configured outstanding state.
- State round-trip for empty, terminal, partial, UNKNOWN, reconciling, reused slots/generations, and execution dedupe entries.
- Snapshot header/payload/checksum/capacity/cross-reference validation.
- Recovery stage predecessor/evidence requirements and permanent no-auto-arm boundary.
- Projector batches, rollback, duplicate insert, checkpoint atomicity, retry bounds, and credential redaction.

### Replay integration tests

- Embedded Media Driver + Archive records a known event stream, snapshot covers a prefix, replay applies the tail, and restored digest equals uninterrupted state.
- Multiple recordings and replay sessions select the configured channel/stream/recording identity only.
- Snapshot position at start/middle/stop, retained-range gap, fragmented messages, previous schema events, exact duplicate, and conflicting duplicate.
- Controlled shutdown proves recording position reaches publication position.

### Chaos tests

- Kill before/after every transition and persistence boundary.
- Archive connection loss/backpressure/recording stop/lag/disk watermark.
- Snapshot partial write, force failure, corrupt checksum, rename interruption, and older-good fallback.
- Venue extra fill after lost crash tail and deterministic compensating convergence.
- Projector disconnect during a batch and restart from last committed checkpoint.

### PostgreSQL integration

- Run against a disposable PostgreSQL database supplied by CI/local Docker.
- Project full archive, capture ordered key/digest, drop projector-owned schema, recreate, rebuild, and compare exactly.
- Confirm duplicate replay changes neither rows nor checkpoint and a failed batch rolls back both.

---

## VALIDATION COMMANDS

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o -pl basis-protocol,basis-core,basis-journal,basis-sim,basis-app -am test

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o -Preplay-tests -pl basis-journal,basis-sim -am verify

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o -Pchaos-tests -pl basis-journal,basis-sim -am verify

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -Pproperty-tests -pl basis-core,basis-journal -am verify

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -Pintegration-tests -pl basis-journal -am verify \
  -Dbasis.test.postgres.url=jdbc:postgresql://localhost:5432/basis_test

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o spotless:check

JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
./mvnw -o -T1C clean verify

python3 -m unittest discover -s tools/tests -v
```

The PostgreSQL command is intentionally online with respect to a local test database. All other Phase 8 tests, including embedded Aeron Archive tests, must run without venue network access or credentials.

---

## ACCEPTANCE CRITERIA

- [ ] Every critical/important event is admitted in one deterministic order with a stable event sequence and versioned SBE encoding.
- [ ] Normal/high-water backpressure disarms initiation before the configured critical reserve is consumed.
- [ ] The calculated reserve carries every maximum configured terminal/risk-reducing transition after initiation stops; reserve exhaustion becomes a global fault.
- [ ] Aeron recording position, publication position, ingress occupancy, lag, liveness, and storage watermarks are observable and drive explicit health states.
- [ ] A verified snapshot contains all non-book state required to retain maximum possible exposure, execution dedupe, generations, rate claims, and kills.
- [ ] Corrupt, truncated, incompatible, or internally inconsistent snapshots never partially mutate recovered state; an older compatible snapshot may be selected.
- [ ] Snapshot + replay produces the same canonical safety state as uninterrupted execution for every kill point.
- [ ] Duplicate events are idempotent; conflicting duplicates, gaps, unknown critical templates, and invalid schema versions fail closed.
- [ ] Crash-tail venue facts append compensating events and converge exposure without editing archived history.
- [ ] Recovery cannot pass any stage without its evidence and cannot reach `ARMED`; the maximum Phase 8 state is `DISARMED_READY`.
- [ ] Both venue reconciliation ports and both configured books must be authoritative/trusted/fresh before `DISARMED_READY`.
- [ ] Projector failure never mutates or blocks core/journal state, and projector credentials are not logged.
- [ ] A deleted PostgreSQL projection rebuilds from retained durable input with identical ordered event keys and digest.
- [ ] Full Java 25 unit, property, replay, chaos, formatting, and reactor validation passes.
- [ ] The operational documentation explicitly states the asynchronous crash-tail durability limitation and manual-arm requirement.

---

## OPEN QUESTIONS / ASSUMPTIONS

- **Assumption — recommended:** v1 embeds `ArchivingMediaDriver`; configuration and interfaces remain compatible with a later external/sidecar Archive. Changing production placement later must not change SBE, ingress, snapshot, or replay semantics.
- **Assumption — recommended:** pgJDBC is pinned to `42.7.13`; implementation must recheck Maven Central and release notes if execution begins substantially after this plan date.
- **Assumption — recommended:** PostgreSQL integration certification uses a disposable local/CI database. No production database or company credentials are needed for Phase 8 implementation.
- **Assumption:** capacities and configuration/catalog generations are immutable across one recovery attempt. Mismatch blocks load; no in-place capacity migration is attempted.
- **Assumption:** future venue gateways can supply bounded reconciliation pages through `VenueRecoveryPort`. Until Phases 9-10, deterministic fake adapters certify the coordinator.
- **Open operational input before production deployment, not before implementation:** archive directory/mount, storage budget, retention duration, disk high/critical watermarks, maximum outstanding children/groups, and desired snapshot cadence. Tests use explicit temporary values; production startup must refuse missing values.

## NOTES

The hard safety boundary is not "the archive has every last byte." V1 deliberately uses asynchronous local durability. The provable claim is narrower: application queues do not silently lose critical facts under certified load; archive lag disarms before reserve exhaustion; restart reconstructs the durable prefix; unresolved exposure remains UNKNOWN; venue truth supplies an append-only compensating tail; books resynchronize; and the system remains disarmed until a human-controlled later phase arms it.

Persisting raw internal arrays was rejected because it would bind recovery to hash-table/free-list layout and make schema evolution unsafe. The snapshot stores logical rows with stable codes and generations, and restoration rebuilds internal indexes after validation.

Separate critical and normal Aeron streams were rejected for v1 because replay would need a cross-stream merge rule to recover one authoritative order. One FIFO plus an admission reserve preserves total order and still protects terminal capacity.

**Confidence score:** 8/10 for one-pass implementation. The main residual risk is the breadth of controlled exact-state restoration and Aeron failure injection; the plan isolates both behind testable interfaces and requires deterministic kill-point certification.

## AMENDMENTS

- None.
