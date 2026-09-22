# Phase 3 market-data transport code review

**Stats:**

- Files Modified: 7
- Files Added: 75 before review/report artifacts
- Files Deleted: 0
- New lines: 4,499 before review/report artifacts
- Deleted lines: 6

Code review passed. No unresolved technical issues detected.

Issues found and fixed during review:

- High: exact venue channel/instrument identity was not enforced, allowing a valid wrong-route frame to be normalized under the configured instrument ID. Feed profiles now contain bounded exact route identity and both parsers reject mismatches.
- Medium: disconnect callbacks could re-enter failure handling and generic disconnect health could overwrite a heartbeat-timeout reason. Sessions now enter backoff before closing and own handled disconnect publication.
- Medium: unchanged unhealthy lane state was counted as work every duty cycle, potentially preventing the core agent from idling. Health is still sampled every cycle, while callbacks are edge-triggered by the health-word version.
- Low: the raw capture drop counter lacked cross-thread visibility. It is now volatile under the documented single-producer model.
- Medium: a failed TCP connection could leave its session in `CONNECTING`. Both concrete connectors now clear the failed channel and notify the session so bounded reconnect backoff begins.

All fixes have focused regression coverage and the complete validation matrix passes.
