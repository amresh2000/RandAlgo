package com.penguinsecure.basis.venue.api.order;

@FunctionalInterface
public interface VenueOrderFactSink {
    boolean publish(MutableVenueOrderFact fact);
}
