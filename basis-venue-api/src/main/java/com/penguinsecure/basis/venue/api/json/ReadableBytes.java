package com.penguinsecure.basis.venue.api.json;

/** Read-only random access to one bounded frame without exposing transport types. */
public interface ReadableBytes {
    byte getByte(int index);
}
