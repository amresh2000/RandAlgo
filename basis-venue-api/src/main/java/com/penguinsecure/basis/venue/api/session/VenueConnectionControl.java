package com.penguinsecure.basis.venue.api.session;

/** Narrow socket control seam implemented by the concrete Netty connection owner. */
public interface VenueConnectionControl {
    void connect();

    void sendText(CharSequence payload);

    void close();
}
