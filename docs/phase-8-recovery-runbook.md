# Phase 8 journal and recovery runbook

Phase 8 adds the durable local boundary. It does not make the archive synchronously durable and it
does not arm trading after a restart. Aeron publication and local recording are asynchronous, so a
process or host failure can lose an admitted crash tail. Recovery reconstructs the verified durable
prefix, marks every nonterminal exposure `UNKNOWN`, and requires authoritative venue reconciliation
to append compensating facts. Archived history is never edited.

## Storage and permissions

Configure distinct absolute paths for the Media Driver, Archive, and snapshots. Production startup
must reject root paths, implicit working-directory paths, and missing capacity/watermark settings.
Run the process under a dedicated account. Directories should be mode `0700`; snapshot files are
published mode `0600`. Put the archive on storage sized for peak encoded event rate multiplied by the
retention window, plus the active segment, two verified snapshots, catalog/mark files, and at least
30% operational headroom.

Alert on archive recording lag, publication disconnection/backpressure, no recording progress,
catalog errors, and both disk watermarks. The high watermark disarms new exposure. Exhausting the
critical reserve, a closed publication, or maximum-position exhaustion is a global fault.

## Critical reserve sizing

The minimum is:

`maximum outstanding children × maximum encoded terminal bytes per child`

Include terminal order facts, reservation/group release, kill/fault, and envelope/ring alignment in
the per-child bound. Startup rejects a configured reserve below the calculated minimum. Important
or exposure-increasing events stop at `lossless capacity - critical reserve`; critical terminal or
risk-reducing facts may consume the reserve.

## Snapshot and retention rules

A snapshot is eligible only when its boundary is covered by the Archive recording position. The
writer creates a same-directory temporary file, forces it, reads it back, verifies SHA-256 and the
compatibility header, atomically renames it, and forces the directory where supported. Keep the
previous verified compatible snapshot until the new file is published.

Purge only stopped archive segments strictly before both the latest verified compatible snapshot
and the committed projector checkpoint. Never purge the active tail or the only recovery base.

## Restart sequence

Recovery is strictly:

`BOOT → SNAPSHOT_LOADED → JOURNAL_REPLAYED → PRIVATE_CONNECTED → ORDERS_RECONCILED → POSITIONS_RECONCILED → BOOKS_TRUSTED → WARMED → DISARMED_READY`

Load into fresh bounded tables, validate capacities/checksums/generations/cross-references, replay
from the exact snapshot position, then reconcile both Bybit and Deribit private truth. Both new-session
books must be trusted and fresh for the complete warm-up window. Any gap, conflicting duplicate,
unknown critical template, invalid reference, or incompatible configuration fails closed.

A venue recovery port may report an authoritative orders or positions result only after all bounded
pages have been consumed, local `UNKNOWN` state has been resolved, and every venue/journal difference
has been admitted as a new compensating fact. A successful private API response alone is not enough.

`DISARMED_READY` is the maximum Phase 8 state. There is no automatic transition to `ARMED`; a later
operator-control phase must provide an explicit audited manual arm.

## PostgreSQL projection and rebuild

Apply `basis-journal/src/main/resources/db/phase-8-projection.sql` to a projector-owned database.
Supply a constructed `DataSource`; do not put URLs or credentials in source or logs. Event inserts
and checkpoint advancement commit in one transaction. PostgreSQL failure stops only the cold
projector and never blocks or mutates core/journal state.

Rebuild requires explicit authorization: truncate only `basis_journal_event` and
`basis_projection_checkpoint`, replay from the earliest retained durable position, and compare the
ordered `(recording_id, fragment_position)` keys, row count, and digest with the pre-rebuild evidence.
