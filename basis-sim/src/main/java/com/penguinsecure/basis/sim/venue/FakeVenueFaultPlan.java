package com.penguinsecure.basis.sim.venue;

/** Fixed scripted outcomes; once consumed, the configured default applies. */
public final class FakeVenueFaultPlan {
    private final FakeVenueOutcome[] outcomes;
    private final FakeVenueOutcome defaultOutcome;
    private int size;
    private int cursor;

    public FakeVenueFaultPlan(final int capacity, final FakeVenueOutcome defaultOutcome) {
        if (capacity <= 0 || defaultOutcome == null) {
            throw new IllegalArgumentException("invalid fault plan");
        }
        outcomes = new FakeVenueOutcome[capacity];
        this.defaultOutcome = defaultOutcome;
    }

    public FakeVenueFaultPlan add(final FakeVenueOutcome outcome) {
        if (outcome == null || size == outcomes.length) {
            throw new IllegalStateException("fault plan is full or invalid");
        }
        outcomes[size++] = outcome;
        return this;
    }

    public FakeVenueOutcome next() {
        return cursor < size ? outcomes[cursor++] : defaultOutcome;
    }

    public int consumed() {
        return cursor;
    }
}
