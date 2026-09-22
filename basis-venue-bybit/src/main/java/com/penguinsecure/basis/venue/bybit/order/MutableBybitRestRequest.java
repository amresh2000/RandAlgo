package com.penguinsecure.basis.venue.bybit.order;

/** Reusable signed HTTP request metadata. String rendering is always redacted. */
public final class MutableBybitRestRequest {
    private String method;
    private String path;
    private String queryOrBody;
    private long timestampMillis;
    private long receiveWindowMillis;
    private final StringBuilder apiKey = new StringBuilder(128);
    private final StringBuilder signature = new StringBuilder(64);

    void set(
            final String newMethod,
            final String newPath,
            final String newQueryOrBody,
            final long newTimestampMillis,
            final long newReceiveWindowMillis,
            final BybitCredentials credentials,
            final CharSequence newSignature) {
        method = newMethod;
        path = newPath;
        queryOrBody = newQueryOrBody;
        timestampMillis = newTimestampMillis;
        receiveWindowMillis = newReceiveWindowMillis;
        apiKey.setLength(0);
        credentials.appendApiKey(apiKey);
        signature.setLength(0);
        signature.append(newSignature);
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String queryOrBody() {
        return queryOrBody;
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    public long receiveWindowMillis() {
        return receiveWindowMillis;
    }

    public CharSequence apiKey() {
        return apiKey;
    }

    public CharSequence signature() {
        return signature;
    }

    public void clearSensitive() {
        for (int index = 0; index < apiKey.length(); index++) apiKey.setCharAt(index, '\0');
        for (int index = 0; index < signature.length(); index++) signature.setCharAt(index, '\0');
        apiKey.setLength(0);
        signature.setLength(0);
    }

    @Override
    public String toString() {
        return "MutableBybitRestRequest[" + method + " " + path + ", credentials=REDACTED]";
    }
}
