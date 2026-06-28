package io.kronikol.gcp;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import io.kronikol.gcp.GcpTracking.GcpTrackingOptions;
import java.net.URI;

/**
 * A google-http-client {@link HttpResponseInterceptor} that auto-captures BigQuery and Cloud Storage HTTP
 * calls — the Java analog of the .NET per-service {@code *TrackingMessageHandler}s. Install it on the GCP
 * client builder via {@link #initializer(GcpTrackingOptions)}:
 *
 * <pre>{@code BigQueryOptions.newBuilder()
 *         .setTransportOptions(...)   // ensure the HTTP transport
 *         .build();                   // and set the request initializer on the underlying client}</pre>
 *
 * <p>On each response it reads the request method + URL and the response status, then routes via
 * {@link GcpHttpTracking} to the BigQuery / Cloud Storage recorder. Pub/Sub is gRPC and uses the
 * {@code Tracking*Client} wrappers instead. The google-http-client SDK is {@code compileOnly}.
 */
public final class GcpHttpTrackingInterceptor implements HttpResponseInterceptor {

    private final GcpTrackingOptions options;

    public GcpHttpTrackingInterceptor(GcpTrackingOptions options) {
        this.options = options;
    }

    /** An {@link HttpRequestInitializer} that installs this interceptor as each request's response interceptor. */
    public static HttpRequestInitializer initializer(GcpTrackingOptions options) {
        GcpHttpTrackingInterceptor interceptor = new GcpHttpTrackingInterceptor(options);
        return (HttpRequest request) -> request.setResponseInterceptor(interceptor);
    }

    @Override
    public void interceptResponse(HttpResponse response) {
        HttpRequest request = response.getRequest();
        if (request == null) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(request.getUrl().build());
        } catch (Exception e) {
            return; // unparseable URL — nothing to classify
        }
        GcpHttpTracking.track(options, request.getRequestMethod(), uri, response.getStatusCode());
    }
}
