package io.kronikol.elasticsearch;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.PhaseConfiguration;
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
        if (suppressedByPhase(options)) {
            return;
        }
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
        record(options, httpMethod, requestUri, body, resultSummary, StatusCode.of("OK"));
    }

    /**
     * As {@link #record(ElasticsearchTrackingOptions, String, URI, String, String)} but with the real HTTP
     * response status — the form a transport hook (e.g. {@link KronikolElasticsearchInterceptor}) uses.
     */
    public static void record(ElasticsearchTrackingOptions options, String httpMethod, URI requestUri,
                              String body, String resultSummary, int statusCode) {
        record(options, httpMethod, requestUri, body, resultSummary, StatusCode.of(statusCode));
    }

    private static void record(ElasticsearchTrackingOptions options, String httpMethod, URI requestUri,
                               String body, String resultSummary, StatusCode statusCode) {
        if (suppressedByPhase(options)) {
            return;
        }
        ElasticsearchOperationInfo info = ElasticsearchOperationClassifier.classify(httpMethod, requestUri);
        TrackingVerbosity verbosity = PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
        String label = ElasticsearchOperationClassifier.getDiagramLabel(info, verbosity);
        URI uri = ElasticsearchOperationClassifier.buildUri(info, verbosity, requestUri);
        String content = verbosity.includesPayload() ? body : null;
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.ELASTICSEARCH, Method.of(label), uri, content,
            statusCode, resultSummary);
    }

    /** Whether the current phase suppresses tracking per the options' {@code trackDuringSetup/Action}. */
    private static boolean suppressedByPhase(ElasticsearchTrackingOptions options) {
        return !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction());
    }

    /** Configuration for Elasticsearch tracking. */
    public record ElasticsearchTrackingOptions(String serviceName, String callerName,
                                               Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity,
                                               boolean trackDuringSetup, boolean trackDuringAction,
                                               TrackingVerbosity setupVerbosity,
                                               TrackingVerbosity actionVerbosity) {

        public ElasticsearchTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
        }

        /** Three-arg shape (default verbosity, both phases tracked) — back-compatible. */
        public ElasticsearchTrackingOptions(String serviceName, String callerName,
                                            Supplier<TestInfo> testInfoFetcher) {
            this(serviceName, callerName, testInfoFetcher, TrackingVerbosity.DEFAULT, true, true, null, null);
        }

        /** Four-arg shape (both phases tracked) — back-compatible. */
        public ElasticsearchTrackingOptions(String serviceName, String callerName,
                                            Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity) {
            this(serviceName, callerName, testInfoFetcher, verbosity, true, true, null, null);
        }

        /** Six-arg shape (no per-phase verbosity overrides) — back-compatible. */
        public ElasticsearchTrackingOptions(String serviceName, String callerName,
                                            Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity,
                                            boolean trackDuringSetup, boolean trackDuringAction) {
            this(serviceName, callerName, testInfoFetcher, verbosity, trackDuringSetup, trackDuringAction,
                null, null);
        }

        public static ElasticsearchTrackingOptions forCluster(String serviceName) {
            return new ElasticsearchTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT, true, true, null, null);
        }

        /** A copy with the given verbosity (Summarised omits the request body). */
        public ElasticsearchTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new ElasticsearchTrackingOptions(serviceName, callerName, testInfoFetcher, value,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity);
        }

        /** A copy with the given test-identity fetcher (the .NET {@code CurrentTestInfoFetcher}). */
        public ElasticsearchTrackingOptions withTestInfoFetcher(java.util.function.Supplier<TestInfo> value) {
            return new ElasticsearchTrackingOptions(serviceName, callerName, value, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity);
        }

        /** A copy that (does not) track during the Setup phase (the .NET {@code TrackDuringSetup}). */
        public ElasticsearchTrackingOptions withTrackDuringSetup(boolean value) {
            return new ElasticsearchTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                value, trackDuringAction, setupVerbosity, actionVerbosity);
        }

        /** A copy that (does not) track during the Action phase (the .NET {@code TrackDuringAction}). */
        public ElasticsearchTrackingOptions withTrackDuringAction(boolean value) {
            return new ElasticsearchTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, value, setupVerbosity, actionVerbosity);
        }

        /** A copy with a Setup-phase verbosity override (the .NET {@code SetupVerbosity}; {@code null} = base). */
        public ElasticsearchTrackingOptions withSetupVerbosity(TrackingVerbosity value) {
            return new ElasticsearchTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, value, actionVerbosity);
        }

        /** A copy with an Action-phase verbosity override (the .NET {@code ActionVerbosity}; {@code null} = base). */
        public ElasticsearchTrackingOptions withActionVerbosity(TrackingVerbosity value) {
            return new ElasticsearchTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, value);
        }
    }
}
