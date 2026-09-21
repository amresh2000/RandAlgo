package com.penguinsecure.basis.core.risk;

/** Fixed-capacity monotonic kill state. A reset requires a strictly newer generation. */
public final class KillHierarchy {
    private final long[][] generations;
    private final boolean[][] killed;

    public KillHierarchy(final int maximumScopeId) {
        if (maximumScopeId <= 0)
            throw new IllegalArgumentException("maximumScopeId must be positive");
        generations = new long[KillScope.values().length][maximumScopeId + 1];
        killed = new boolean[KillScope.values().length][maximumScopeId + 1];
    }

    public KillUpdateStatus kill(final KillScope scope, final int scopeId, final long generation) {
        if (!valid(scope, scopeId, generation)) return KillUpdateStatus.INVALID_ARGUMENT;
        final int row = scope.code();
        if (generation < generations[row][scopeId]) return KillUpdateStatus.STALE_GENERATION;
        if (generation == generations[row][scopeId] && killed[row][scopeId]) {
            return KillUpdateStatus.IDEMPOTENT;
        }
        generations[row][scopeId] = generation;
        killed[row][scopeId] = true;
        return KillUpdateStatus.APPLIED;
    }

    public KillUpdateStatus reset(final KillScope scope, final int scopeId, final long generation) {
        if (!valid(scope, scopeId, generation)) return KillUpdateStatus.INVALID_ARGUMENT;
        final int row = scope.code();
        if (generation <= generations[row][scopeId]) return KillUpdateStatus.STALE_GENERATION;
        generations[row][scopeId] = generation;
        killed[row][scopeId] = false;
        return KillUpdateStatus.APPLIED;
    }

    public boolean isKilled(final KillScope scope, final int scopeId) {
        return scope != null
                && scopeId >= 0
                && scopeId < killed[scope.code()].length
                && killed[scope.code()][scopeId];
    }

    public boolean canAddress(final KillScope scope, final int scopeId) {
        return scope != null
                && scopeId >= 0
                && scopeId < killed[scope.code()].length
                && (scope != KillScope.GLOBAL || scopeId == 0);
    }

    public long generation(final KillScope scope, final int scopeId) {
        if (scope == null || scopeId < 0 || scopeId >= generations[scope.code()].length) return 0;
        return generations[scope.code()][scopeId];
    }

    private boolean valid(final KillScope scope, final int scopeId, final long generation) {
        return scope != null
                && scopeId >= 0
                && scopeId < killed[scope.code()].length
                && generation > 0
                && (scope != KillScope.GLOBAL || scopeId == 0);
    }
}
