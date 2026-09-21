# Phase 6 risk and execution code review

**Stats:**

- Production Java files added: 40, including the JMH benchmark
- Test Java files added: 10
- Core production and test lines added: 3,816 before report artifacts
- Files deleted: 0

Code review passed. No unresolved high, medium, or low technical findings remain.

Issues found and fixed during review:

- High: reservations initially accounted for gross and collateral but not aggregate pending net and unhedged exposure. The strategy ledger and reservation table now reserve and release all four dimensions, and concurrent groups are regression-tested against the shared net limit.
- High: emergency unwind used hedge-leg quantity when the hedge ratio differed from one. It now reverses the actual incremental initiating fill quantity; a 2:1 hedge-ratio exhaustion test proves the behavior.
- High: an execution plan could initially diverge from the request that risk approved. Start now binds strategy/configuration, routes, session generations, instruments, native quantities, prices, and deadline to the approved request before reserving or publishing.
- High: a caller could label an order risk-reducing without proving it against current exposure. Emergency bypass now requires existing net exposure, checked signed reduction, and a conservative before/after bound.
- Medium: pending command publication failures leaked normal, hedge, or emergency rate capacity. Unsent release and failed hedge/unwind publication now restore only the claims that never reached a venue.
- Medium: repeated successful write facts were not fully idempotent because the reservation's SENT transition rejected duplicates. SENT is now idempotent, while conflicting child/group associations fail closed.
- Medium: child and group tables lacked safe terminal slot reuse. Terminal release now clears bounded identity indexes, increments slot generations on reuse, and rejects stale handles.
- Medium: SPSC producer/consumer health fields lacked cross-thread visibility. Sequence and failure counters are now volatile in addition to release/acquire publication markers.
- Low: cancel/reject terminal transitions were declared but not implemented. Explicit idempotent transitions and tests were added.

Validation after fixes covers request/plan identity, ordered risk rejection, aggregate reservations, ambiguity retention, partial-fill hedging, emergency unwind, execution deduplication, terminal slot reuse, urgent priority, property conservation, and allocation-free command transport.
