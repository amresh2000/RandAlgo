package com.penguinsecure.basis.venue.api.session;

/** Fixed-capacity, single-thread-owned subscription union. */
public final class SubscriptionSet {
    private static final int MAXIMUM_CHANNEL_LENGTH = 256;
    private final String[] channels;
    private int size;

    public SubscriptionSet(final int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        channels = new String[capacity];
    }

    public int size() {
        return size;
    }

    public String channel(final int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return channels[index];
    }

    public boolean add(final String channel) {
        if (channel == null || channel.isEmpty() || channel.length() > MAXIMUM_CHANNEL_LENGTH)
            throw new IllegalArgumentException("channel is required");
        for (int i = 0; i < size; i++) if (channels[i].equals(channel)) return true;
        if (size == channels.length) return false;
        channels[size++] = channel;
        return true;
    }

    public boolean remove(final String channel) {
        for (int i = 0; i < size; i++) {
            if (channels[i].equals(channel)) {
                channels[i] = channels[--size];
                channels[size] = null;
                return true;
            }
        }
        return false;
    }
}
