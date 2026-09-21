package com.penguinsecure.basis.sim.venue;

import com.penguinsecure.basis.core.book.FixedDepthOrderBook;

@FunctionalInterface
public interface SimulationBookLookup {
    FixedDepthOrderBook find(int venueId, int instrumentId);
}
