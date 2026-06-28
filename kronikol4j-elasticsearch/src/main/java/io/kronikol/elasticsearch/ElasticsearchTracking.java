package io.kronikol.elasticsearch;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Records an Elasticsearch / OpenSearch operation as a tracked interaction (the reusable core a
 * client wrapper delegates to). Elasticsearch category renders as a {@code database} participant —
 * the same direct-log pattern as the Mongo/Cassandra trackers.
 */
public final class ElasticsearchTracking {

    private static final URI ELASTICSEARCH_URI = URI.create("elasticsearch://database/");

    private ElasticsearchTracking() {
    }

    /**
     * @param operation the operation (e.g. {@code search}, {@code index}, {@code get}, {@code delete},
     *                  {@code bulk})
     * @param index     the target index (or alias)
     * @param body      the query/document body (or a redacted form)
     * @param resultSummary a short human description of the outcome (e.g. {@code "3 hits"})
     */
    public static void record(ElasticsearchTrackingOptions options, String operation, String index,
                              String body, String resultSummary) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        String request = index + ": " + (body == null ? "" : body);
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.ELASTICSEARCH, Method.of(operation.toUpperCase(Locale.ROOT)),
            ELASTICSEARCH_URI, request, StatusCode.of("OK"), resultSummary);
    }

    /**
     * Records an interaction the way a real ES client transport hook would — classifying the request from its
     * HTTP method + URI via {@link ElasticsearchOperationClassifier} (the .NET callback-handler core). The
     * diagram label + {@code elasticsearch:///<index>} URI come from the classifier; the body is honoured per
     * the configured {@link TrackingVerbosity} (Summarised omits it). Prefer this over the manual overload
     * when you have the request's HTTP method/URI.
     *
     * @param httpMethod    the request method (e.g. {@code GET}, {@code POST}, {@code PUT}, {@code DELETE})
     * @param requestUri    the request URI (path drives index / document-id / operation classification)
     * @param body          the query/document body (or a redacted form); dropped at Summarised verbosity
     * @param resultSummary a short outcome description (e.g. {@code "3 hits"})
     */
    public static void record(ElasticsearchTrackingOptions options, String httpMethod, URI requestUri,
                              String body, String resultSummary) {
        ElasticsearchOperationInfo info = ElasticsearchOperationClassifier.classify(httpMethod, requestUri);
        TrackingVerbosity verbosity = options.verbosity();
        String label = ElasticsearchOperationClassifier.getDiagramLabel(info, verbosity);
        URI uri = ElasticsearchOperationClassifier.buildUri(info, verbosity, requestUri);
        String content = verbosity.includesPayload() ? body : null;
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.ELASTICSEARCH, Method.of(label), uri, content,
            StatusCode.of("OK"), resultSummary);
    }

    /** Configuration for Elasticsearch tracking. */
    public record ElasticsearchTrackingOptions(String serviceName, String callerName,
                                               Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity) {

        public ElasticsearchTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
        }

        /** Three-arg shape (default verbosity) — the back-compatible constructor. */
        public ElasticsearchTrackingOptions(String serviceName, String callerName,
                                            Supplier<TestInfo> testInfoFetcher) {
            this(serviceName, callerName, testInfoFetcher, TrackingVerbosity.DEFAULT);
        }

        public static ElasticsearchTrackingOptions forCluster(String serviceName) {
            return new ElasticsearchTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT);
        }

        /** A copy with the given verbosity (Summarised omits the request body). */
        public ElasticsearchTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new ElasticsearchTrackingOptions(serviceName, callerName, testInfoFetcher, value);
        }
    }
}
