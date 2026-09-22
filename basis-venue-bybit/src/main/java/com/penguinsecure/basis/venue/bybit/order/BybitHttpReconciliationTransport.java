package com.penguinsecure.basis.venue.bybit.order;

import com.penguinsecure.basis.core.time.EpochClock;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Synchronous warm-path HTTP transport for bounded Bybit order reconciliation. */
public final class BybitHttpReconciliationTransport
        implements BybitReconciliationTransport, AutoCloseable {
    private final HttpClient client;
    private final URI restEndpoint;
    private final BybitCredentials credentials;
    private final BybitRestRequestSigner signer;
    private final BybitReconciliationResponseParser parser;
    private final EpochClock epochClock;
    private final MutableBybitRestRequest signed = new MutableBybitRestRequest();
    private final long sessionGeneration;
    private final int maximumResponseBytes;
    private final Duration timeout;

    @SuppressWarnings("ParameterNumber")
    public BybitHttpReconciliationTransport(
            final HttpClient client,
            final URI restEndpoint,
            final BybitCredentials credentials,
            final BybitRestRequestSigner signer,
            final BybitReconciliationResponseParser parser,
            final EpochClock epochClock,
            final long sessionGeneration,
            final int maximumResponseBytes,
            final Duration timeout) {
        if (client == null
                || restEndpoint == null
                || credentials == null
                || signer == null
                || parser == null
                || epochClock == null
                || timeout == null)
            throw new NullPointerException("transport dependencies are required");
        if (!"https".equalsIgnoreCase(restEndpoint.getScheme())
                || restEndpoint.getHost() == null
                || sessionGeneration <= 0
                || maximumResponseBytes <= 0
                || maximumResponseBytes == Integer.MAX_VALUE
                || timeout.isZero()
                || timeout.isNegative())
            throw new IllegalArgumentException("invalid transport bounds");
        this.client = client;
        this.restEndpoint = restEndpoint;
        this.credentials = credentials;
        this.signer = signer;
        this.parser = parser;
        this.epochClock = epochClock;
        this.sessionGeneration = sessionGeneration;
        this.maximumResponseBytes = maximumResponseBytes;
        this.timeout = timeout;
    }

    @Override
    public boolean fetch(
            final BybitReconciliationEndpoint endpoint,
            final byte[] cursor,
            final int cursorLength,
            final long startTimeMillis,
            final BybitReconciliationPage destination) {
        if (endpoint != BybitReconciliationEndpoint.OPEN_ORDERS
                && endpoint != BybitReconciliationEndpoint.ORDER_HISTORY) return false;
        final String query =
                signer.orderQuery(
                        endpoint == BybitReconciliationEndpoint.OPEN_ORDERS ? 0 : startTimeMillis,
                        cursor,
                        cursorLength);
        final String path = signer.path(endpoint);
        signer.sign("GET", path, query, epochClock.epochNanos() / 1_000_000L, credentials, signed);
        try {
            final URI uri = restEndpoint.resolve(path + "?" + query);
            final HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .timeout(timeout)
                            .header("X-BAPI-API-KEY", signed.apiKey().toString())
                            .header("X-BAPI-TIMESTAMP", Long.toString(signed.timestampMillis()))
                            .header(
                                    "X-BAPI-RECV-WINDOW",
                                    Long.toString(signed.receiveWindowMillis()))
                            .header("X-BAPI-SIGN", signed.signature().toString())
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
                    && parser.parse(body, body.length, sessionGeneration, destination)
                            == BybitOrderParseStatus.OK;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            signed.clearSensitive();
        }
    }

    @Override
    public void close() {
        signed.clearSensitive();
        credentials.close();
    }
}
