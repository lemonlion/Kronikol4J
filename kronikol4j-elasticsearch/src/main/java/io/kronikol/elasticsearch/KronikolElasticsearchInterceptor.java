package io.kronikol.elasticsearch;

import io.kronikol.elasticsearch.ElasticsearchTracking.ElasticsearchTrackingOptions;
import java.net.URI;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

/**
 * An Apache HttpCore {@link HttpResponseInterceptor} that auto-captures Elasticsearch / OpenSearch REST calls
 * — the Java analog of the .NET {@code ElasticsearchTrackingCallbackHandler}. The official ES/OpenSearch Java
 * clients run on the low-level {@code RestClient} (Apache HttpAsyncClient); register this via the client
 * config callback:
 *
 * <pre>{@code RestClient.builder(host)
 *         .setHttpClientConfigCallback(b -> b.addInterceptorLast(
 *             new KronikolElasticsearchInterceptor(ElasticsearchTrackingOptions.forCluster("Search"))))
 *         .build();}</pre>
 *
 * <p>On each response it reconstructs the absolute request URI from the context's target host + request line,
 * then delegates to {@link ElasticsearchTracking#record} — which classifies the request (method + path) and
 * emits the pair with the classifier's label, the {@code elasticsearch:///<index>} URI, the real status, and
 * per-phase verbosity. The httpcore SDK is {@code compileOnly}.
 */
public final class KronikolElasticsearchInterceptor implements HttpResponseInterceptor {

    private final ElasticsearchTrackingOptions options;

    public KronikolElasticsearchInterceptor(ElasticsearchTrackingOptions options) {
        this.options = options;
    }

    @Override
    public void process(HttpResponse response, HttpContext context) {
        HttpCoreContext ctx = HttpCoreContext.adapt(context);
        HttpRequest request = ctx.getRequest();
        if (request == null) {
            return;
        }
        URI uri = absoluteUri(ctx.getTargetHost(), request.getRequestLine().getUri());
        if (uri == null) {
            return;
        }
        track(request.getRequestLine().getMethod(), uri, response.getStatusLine().getStatusCode());
    }

    /** The recording core (package-private for unit tests): classify + emit via the ES recorder. */
    void track(String method, URI uri, int statusCode) {
        ElasticsearchTracking.record(options, method, uri, null, null, statusCode);
    }

    /** Combines the target host with the (usually relative) request-line URI into an absolute URI. */
    private static URI absoluteUri(HttpHost targetHost, String requestUri) {
        try {
            URI parsed = URI.create(requestUri);
            if (parsed.isAbsolute() || targetHost == null) {
                return parsed;
            }
            return URI.create(targetHost.toURI() + requestUri);
        } catch (Exception e) {
            return null; // unparseable URI — capture nothing rather than fail
        }
    }
}
