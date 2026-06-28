package io.kronikol.azure;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.util.BinaryData;
import io.kronikol.azure.AzureTracking.AzureTrackingOptions;
import java.net.URI;
import java.util.Locale;
import reactor.core.publisher.Mono;

/**
 * An Azure SDK {@link HttpPipelinePolicy} that auto-captures Cosmos DB, Blob Storage and Storage Queues REST
 * calls — the unified Java analog of the .NET per-service {@code *TrackingMessageHandler}s. Add it to any of
 * those clients' pipelines via {@code …Builder.addPolicy(...)}.
 *
 * <p>After each exchange it detects the service from the endpoint host and delegates to the matching
 * {@link AzureTracking} recorder: Cosmos ({@code *.documents.azure.com}, reading the
 * {@code x-ms-documentdb-isquery}/{@code -is-upsert} flags), Blob ({@code *.blob.core.windows.net}) and
 * Storage Queues ({@code *.queue.core.windows.net}). Unrecognised hosts are passed through untracked. The
 * Azure SDK is {@code compileOnly}. (For a queue-only client, {@link KronikolAzureStorageQueuePolicy} is an
 * equivalent focused alternative.)
 */
public final class KronikolAzureTrackingPolicy implements HttpPipelinePolicy {

    private static final HttpHeaderName IS_QUERY = HttpHeaderName.fromString("x-ms-documentdb-isquery");
    private static final HttpHeaderName IS_UPSERT = HttpHeaderName.fromString("x-ms-documentdb-is-upsert");

    private final AzureTrackingOptions options;

    public KronikolAzureTrackingPolicy(AzureTrackingOptions options) {
        this.options = options;
    }

    @Override
    public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
        HttpRequest request = context.getHttpRequest();
        String body = readBody(request); // read up-front (the body is consumed once sent)
        return next.process().map(response -> {
            track(request, body, response.getStatusCode());
            return response;
        });
    }

    /**
     * The recording core: detect the Azure service from {@code request} and emit via the matching
     * {@link AzureTracking} recorder. Package-private so it can be unit-tested with a real {@link HttpRequest}.
     */
    void track(HttpRequest request, String body, int statusCode) {
        URI uri;
        try {
            uri = request.getUrl().toURI();
        } catch (Exception e) {
            return;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String method = request.getHttpMethod().toString();

        if (host.contains(".documents.azure.com")) {
            boolean isQuery = "true".equalsIgnoreCase(headerValue(request, IS_QUERY));
            boolean isUpsert = "true".equalsIgnoreCase(headerValue(request, IS_UPSERT));
            AzureTracking.cosmos(options, method, uri, isQuery, isUpsert, body, statusCode);
        } else if (host.contains(".blob.core.windows.net")) {
            AzureTracking.blob(options, method, uri, body, statusCode);
        } else if (host.contains(".queue.core.windows.net")) {
            AzureTracking.storageQueue(options, method, uri, body, statusCode);
        }
        // else: not a recognised Azure service host — passed through untracked
    }

    private static String headerValue(HttpRequest request, HttpHeaderName name) {
        try {
            return request.getHeaders().getValue(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readBody(HttpRequest request) {
        try {
            BinaryData body = request.getBodyAsBinaryData();
            return body == null ? null : body.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
