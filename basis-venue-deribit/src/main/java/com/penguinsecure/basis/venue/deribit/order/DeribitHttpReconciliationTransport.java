package com.penguinsecure.basis.venue.deribit.order;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Synchronous warm-path OAuth HTTP transport with bounded response bodies. */
public final class DeribitHttpReconciliationTransport
        implements DeribitReconciliationTransport, AutoCloseable {
    private final HttpClient client;
    private final URI endpoint;
    private final DeribitTokenState tokens;
    private final DeribitOrderProfile profile;
    private final DeribitReconciliationResponseParser parser;
    private final int pageSize, maximumResponseBytes;
    private final Duration timeout;
    private final StringBuilder bearer = new StringBuilder(4096);

    @SuppressWarnings("ParameterNumber")
    public DeribitHttpReconciliationTransport(
            final HttpClient client,
            final URI endpoint,
            final DeribitTokenState tokens,
            final DeribitOrderProfile profile,
            final DeribitReconciliationResponseParser parser,
            final int pageSize,
            final int maximumResponseBytes,
            final Duration timeout) {
        if (client == null
                || endpoint == null
                || tokens == null
                || profile == null
                || parser == null
                || timeout == null) throw new NullPointerException("dependencies are required");
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || pageSize <= 0
                || pageSize > 1000
                || maximumResponseBytes <= 0
                || maximumResponseBytes == Integer.MAX_VALUE
                || timeout.isZero()
                || timeout.isNegative())
            throw new IllegalArgumentException("invalid transport bounds");
        this.client = client;
        this.endpoint = endpoint;
        this.tokens = tokens;
        this.profile = profile;
        this.parser = parser;
        this.pageSize = pageSize;
        this.maximumResponseBytes = maximumResponseBytes;
        this.timeout = timeout;
    }

    @Override
    public boolean fetch(
            final DeribitReconciliationEndpoint evidence,
            final int offset,
            final long startTimeMillis,
            final DeribitReconciliationPage destination) {
        if ((evidence != DeribitReconciliationEndpoint.OPEN_ORDERS
                        && evidence != DeribitReconciliationEndpoint.ORDER_HISTORY)
                || offset < 0
                || startTimeMillis < 0
                || !tokens.available()) return false;
        final String query =
                evidence == DeribitReconciliationEndpoint.OPEN_ORDERS
                        ? "instrument_name=" + encode(profile.instrument())
                        : "currency="
                                + encode(profile.currency())
                                + "&count="
                                + pageSize
                                + "&offset="
                                + offset
                                + "&include_old=true&include_unfilled=true";
        bearer.setLength(0);
        bearer.append("Bearer ");
        tokens.appendAccessToken(bearer);
        try {
            final URI uri = endpoint.resolve(evidence.path() + "?" + query);
            final HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .timeout(timeout)
                            .header("Authorization", bearer.toString())
                            .GET()
                            .build();
            final HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            final byte[] body;
            try (InputStream stream = response.body()) {
                body = stream.readNBytes(maximumResponseBytes + 1);
            }
            return response.statusCode() == 200
                    && body.length <= maximumResponseBytes
                    && parser.parse(body, body.length, destination) == DeribitOrderParseStatus.OK;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            erase(bearer);
        }
    }

    @Override
    public void close() {
        erase(bearer);
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private static void erase(final StringBuilder value) {
        for (int i = 0; i < value.length(); i++) value.setCharAt(i, '\0');
        value.setLength(0);
    }
}
