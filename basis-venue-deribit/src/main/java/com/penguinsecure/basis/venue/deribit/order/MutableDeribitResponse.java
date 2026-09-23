package com.penguinsecure.basis.venue.deribit.order;

/** Reusable parsed JSON-RPC control/command response. Token bytes never enter diagnostics. */
public final class MutableDeribitResponse {
    private final byte[] accessToken = new byte[4096];
    private final byte[] refreshToken = new byte[4096];
    private long requestId;
    private int errorCode;
    private int accessLength;
    private int refreshLength;
    private long expiresInSeconds;
    private boolean result;
    private boolean heartbeatTest;

    void reset() {
        requestId = 0;
        errorCode = 0;
        accessLength = 0;
        refreshLength = 0;
        expiresInSeconds = 0;
        result = false;
        heartbeatTest = false;
    }

    void requestId(final long value) {
        requestId = value;
    }

    void errorCode(final int value) {
        errorCode = value;
    }

    void result(final boolean value) {
        result = value;
    }

    void heartbeatTest(final boolean value) {
        heartbeatTest = value;
    }

    void accessLength(final int value) {
        accessLength = value;
    }

    void refreshLength(final int value) {
        refreshLength = value;
    }

    void expiresInSeconds(final long value) {
        expiresInSeconds = value;
    }

    byte[] mutableAccessToken() {
        return accessToken;
    }

    byte[] mutableRefreshToken() {
        return refreshToken;
    }

    public long requestId() {
        return requestId;
    }

    public int errorCode() {
        return errorCode;
    }

    public boolean hasResult() {
        return result;
    }

    public boolean heartbeatTest() {
        return heartbeatTest;
    }

    public byte[] accessToken() {
        return accessToken;
    }

    public byte[] refreshToken() {
        return refreshToken;
    }

    public int accessLength() {
        return accessLength;
    }

    public int refreshLength() {
        return refreshLength;
    }

    public long expiresInSeconds() {
        return expiresInSeconds;
    }

    public boolean hasTokens() {
        return accessLength > 0 && refreshLength > 0 && expiresInSeconds > 0;
    }
}
