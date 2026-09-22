# Phase 8 execution report: journal, snapshots, replay, and recovery

## Outcome

Implemented the Phase 8 durable recovery boundary on `feature/phase-8-journal-recovery`. The system
now admits safety events through bounded reserve-aware queues, records them with an embedded Aeron
Archive, snapshots complete non-book core state atomically, replays full-after-state SBE facts into
fresh bounded tables, gates restart through `DISARMED_READY`, and maintains an independently
rebuildable PostgreSQL projection.

No Bybit or Deribit private credentials are required by this phase. Authenticated reconciliation
adapters remain Phase 9/10 work; Phase 8 defines and tests their fail-closed recovery ports.

## Implemented

- Evolved schema `1001` append-only to version 2 with reservation, order-fact, kill-state, and
  journal-fault templates plus complete recovery fields and v0/v1/v2 compatibility goldens.
- Added controlled restore/rebuild seams for ledgers, reservations, token buckets, execution groups,
  child orders, execution dedupe identities, kill generations, and released-slot generations.
- Added a bounded lossless FIFO with a byte-counted critical reserve, an isolated lossy lane,
  contiguous admission sequencing, deterministic classifications, and explicit health/fault states.
- Added embedded Aeron Media Driver/Archive lifecycle, local IPC recording, publication-result
  mapping, lag/recording observation, controlled drain, and safe retention preconditions.
- Added a canonical logical snapshot codec and atomic file store with restrictive permissions,
  header-plus-payload SHA-256, fsync/read-back verification, corrupt-newest fallback, and fresh-state
  validation before exposure.
- Added archive replay with retained-range checks, fragment assembly, exact duplicate handling,
  conflicting-duplicate and sequence-gap rejection, explicit schema/template failures, and
  side-effect-free core fact application.
- Added the exact recovery stage sequence ending at `DISARMED_READY`, with two-venue and two-book
  evidence gates and no arming transition.
- Added a cold pgJDBC projector with raw SBE storage, transactional idempotent batch/checkpoint
  commits, bounded retry behavior, and explicitly authorized owned-table rebuild.
- Added the operational recovery runbook and certification tests for ingress properties, snapshot
  interruption/corruption, archive backpressure/faults, embedded recording, restart at every durable
  transition boundary, and PostgreSQL rebuild.

## Plan adaptations

- Embedded Archive control uses local IPC rather than UDP loopback. This avoids a needless socket
  dependency for the single-JVM v1 topology while preserving the external-runtime seam.
- Snapshot payload layout is repository-owned and logical; internal free-list/hash layouts are
  rebuilt. Released-slot generations are persisted to retain stale-handle fencing after restart.
- Real private venue reconciliation payload collection is intentionally not implemented here, as
  specified by the plan's Phase 9/10 boundary. `VenueRecoveryPort` cannot report authoritative until
  its adapter has resolved/appended any compensating facts.
- The kill-point replay matrix lives in `basis-journal` because that module owns snapshot/replay and
  can consume core state without introducing a reverse production dependency into `basis-sim`.

## Validation evidence

- `./mvnw -o -pl basis-journal -am test` — passed (protocol/core/journal unit suite).
- `./mvnw -o -Preplay-tests -pl basis-journal,basis-sim -am verify` — passed, including embedded
  Aeron recording and every-boundary snapshot/tail replay convergence.
- `./mvnw -o -Pchaos-tests -pl basis-journal,basis-sim -am verify` — passed.
- `./mvnw -o -Pproperty-tests -pl basis-core,basis-journal -am verify` — passed; 20 generated
  contiguous-admission cases.
- Disposable PostgreSQL 17 integration — passed duplicate replay and authorized delete/rebuild.
- `./mvnw -o -T1C clean verify` — passed all 12 reactor modules on Java 25, including architecture,
  formatting, dependency convergence, and forbidden-API gates.
- `python3 -m unittest discover -s tools/tests -v` — passed 4 tests.

## Operational boundary

The archive remains asynchronous. A crash can lose the admitted but unrecorded tail. Recovery keeps
nonterminal exposure conservative/unknown, requires authoritative truth from both venues plus fresh
books, and never arms automatically. Production directory sizing, mount selection, watermarks,
retention window, and snapshot cadence remain deployment inputs for Phase 11.
