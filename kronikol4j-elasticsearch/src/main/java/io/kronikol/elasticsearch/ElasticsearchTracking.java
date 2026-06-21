package io.kronikol.elasticsearch;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
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

    /** Configuration for Elasticsearch tracking. */
    public record ElasticsearchTrackingOptions(String serviceName, String callerName,
                                               Supplier<TestInfo> testInfoFetcher) {
        public static ElasticsearchTrackingOptions forCluster(String serviceName) {
            return new ElasticsearchTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null);
        }
    }
}
