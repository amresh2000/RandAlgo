package com.penguinsecure.basis.sim.venue;

import com.penguinsecure.basis.core.command.MutableOrderCommand;
import com.penguinsecure.basis.core.command.OrderCommandHandler;

/** Deterministic command dispatcher for a fixed set of fake venues. */
public final class FakeVenueRouter implements OrderCommandHandler {
    private final FakeVenue[] venues;
    private long unmatchedCommands;

    public FakeVenueRouter(final FakeVenue... venues) {
        if (venues == null || venues.length == 0) {
            throw new IllegalArgumentException("at least one venue is required");
        }
        this.venues = venues.clone();
        for (int left = 0; left < this.venues.length; left++) {
            if (this.venues[left] == null) throw new IllegalArgumentException("venue is required");
            for (int right = left + 1; right < this.venues.length; right++) {
                if (this.venues[left].venueId() == this.venues[right].venueId()) {
                    throw new IllegalArgumentException("duplicate venue ID");
                }
            }
        }
    }

    @Override
    public void onCommand(final MutableOrderCommand command) {
        for (FakeVenue venue : venues) {
            if (venue.venueId() == command.venueId()) {
                venue.onCommand(command);
                return;
            }
        }
        unmatchedCommands++;
    }

    public FakeVenue byProducerId(final int producerId) {
        for (FakeVenue venue : venues) {
            if (venue.producerId() == producerId) return venue;
        }
        return null;
    }

    public long unmatchedCommands() {
        return unmatchedCommands;
    }
}
