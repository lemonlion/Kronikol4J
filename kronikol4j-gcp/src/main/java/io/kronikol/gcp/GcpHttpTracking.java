package io.kronikol.gcp;

import io.kronikol.gcp.GcpTracking.GcpTrackingOptions;
import java.net.URI;
import java.util.Locale;

/**
 * Routes a GCP HTTP request to the matching {@link GcpTracking} recorder by inspecting its URL path —
 * the reusable core the {@link GcpHttpTrackingInterceptor} delegates to. BigQuery ({@code /bigquery/}) and
 * Cloud Storage ({@code /storage/}) are the two HTTP-based GCP services (Pub/Sub is gRPC — see the
 * {@code Tracking*Client} wrappers). Pure logic — no GCP SDK dependency.
 */
public final class GcpHttpTracking {

    private GcpHttpTracking() {
    }

    /** Detects the service from the request URI path and emits via the matching recorder; no-op otherwise. */
    public static void track(GcpTrackingOptions options, String httpMethod, URI requestUri, int statusCode) {
        if (requestUri == null) {
            return;
        }
        String path = requestUri.getPath() == null ? "" : requestUri.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("/bigquery/")) {
            GcpTracking.bigQuery(options, httpMethod, requestUri, null, statusCode);
        } else if (path.contains("/storage/")) {
            GcpTracking.cloudStorage(options, httpMethod, requestUri, null, statusCode);
        }
        // else: not a recognised GCP HTTP service path — passed through untracked
    }
}
