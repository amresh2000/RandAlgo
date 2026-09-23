# Phase 12 testnet certification review

**Stats:**

- Files Modified: 3
- Files Added: 23
- Files Deleted: 0
- New lines: 1,508
- Deleted lines: 4

severity: high  
file: basis-app/src/main/java/com/penguinsecure/basis/app/certification/CertificationReportWriter.java  
line: 13  
issue: The initial report API accepted a caller-supplied aggregate decision.  
detail: A caller could pair an incomplete evidence bundle with a forged `CERTIFIED` result and serialize an apparently valid report.  
suggestion: Recalculate the gate result inside the writer so serialized decisions can only derive from the serialized bundle.  
resolution: Fixed. `write` now accepts only the bundle and output stream and evaluates `CertificationGate` internally. A regression test proves an incomplete bundle serializes as `INCOMPLETE`.

No unresolved technical issues detected after the fix. The review also verified
that duplicate soak windows fail closed, a seven-day soak cannot substitute for
the separate 24-hour run, passed scenarios require timestamped and hashed state
evidence, fixture paths cannot escape their manifest directory, and the
capability documents do not claim that live testnet certification has occurred.
