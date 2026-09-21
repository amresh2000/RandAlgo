# Phase 5 opportunity engine code review

**Stats:**

- Files Modified: 4
- Files Added: 40 before report artifacts
- Files Deleted: 0
- New lines: 3,690 before report artifacts
- Deleted lines: 1

Code review passed. No unresolved technical issues detected.

Issues found and fixed during review:

- High: valid inverse exposure calculations could fail with `OVERFLOW` when the final quotient fit in a `long` but the scaled numerator did not. `CheckedDecimalMath.multiplyDivide` now uses an allocation-free positive 128-bit fallback and retains fail-closed overflow for out-of-range quotients.
- Medium: the initial model registry treated carry, signal, and execution-policy IDs as presence markers. All model categories now resolve to explicit compiled objects, and pricing delegates carry and signal behavior through those registered contracts.
- Medium: a shared conversion input could not reproduce strategies whose two legs settle in different currencies. Economic inputs and opportunity evidence now carry independent first- and second-leg conversion rates and generations.
- Medium: inverse bid-side capacity initially used the deepest bid, which could overstate canonical liquidity. Capacity now uses the highest bid as the conservative inverse-price boundary.
- Low: duplicate liquidity-risk table keys could make configuration precedence implicit. Startup construction now rejects duplicate direction/venue/size/regime rows.
- Low: reusing an opportunity result after rejection could retain stale evidence. Every rejection now clears the complete prior decision tuple.

Focused regression and property coverage was added for each numeric and configuration fix.
