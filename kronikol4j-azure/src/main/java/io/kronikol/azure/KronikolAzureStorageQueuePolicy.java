package io.kronikol.azure;

import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.util.BinaryData;
import io.kronikol.azure.AzureTracking.AzureTrackingOptions;
import java.net.URI;
import reactor.core.publisher.Mono;

/**
 * An Azure SDK {@link HttpPipelinePolicy} that auto-captures Azure Storage Queues REST calls — the Java
 * analog of the .NET {@code StorageQueueTrackingMessageHandler} ({@code DelegatingHandler}). Add it to the
 * queue client's pipeline:
 *
 * <pre>{@code new QueueClientBuilder()
 *         .connectionString(cs).queueName("orders")
 *         .addPolicy(new KronikolAzureStorageQueuePolicy(AzureTrackingOptions.forService("Queues")))
 *         .buildClient();}</pre>
 *
 * <p>After each exchange it extracts the request method, URL and body, reads the response status, and
 * delegates to {@link AzureTracking#storageQueue} — which classifies the {@code /{queue}/messages} request,
 * applies phase/verbosity, and emits the {@code MessageQueue} pair with the {@code storagequeue:///<queue>}
 * URI. The Azure SDK is {@code compileOnly}.
 */
public final class KronikolAzureStorageQueuePolicy implements HttpPipelinePolicy {

    private final AzureTrackingOptions options;

    public KronikolAzureStorageQueuePolicy(AzureTrackingOptions options) {
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
     * The recording core: extract method/URI from {@code request} and emit via
     * {@link AzureTracking#storageQueue}. Package-private so it can be unit-tested with a real
     * {@link HttpRequest} (no live pipeline).
     */
    void track(HttpRequest request, String body, int statusCode) {
        URI uri;
        try {
            uri = request.getUrl().toURI();
        } catch (Exception e) {
            return; // malformed URL — nothing to classify
        }
        AzureTracking.storageQueue(options, request.getHttpMethod().toString(), uri, body, statusCode);
    }

    private static String readBody(HttpRequest request) {
        try {
            BinaryData body = request.getBodyAsBinaryData();
            return body == null ? null : body.toString();
        } catch (Exception e) {
            return null; // unreadable / streaming body — capture nothing rather than fail
        }
    }
}
