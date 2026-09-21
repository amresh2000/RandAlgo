package com.penguinsecure.basis.venue.api.session;

/** Secret-owning authentication adapter; the session never reads credential/token text. */
public interface VenueAuthentication {
    void authenticate(VenueConnectionControl connection);

    boolean refreshRequired(long epochNanos);

    void refresh(VenueConnectionControl connection);
}
