package io.kronikol.mongodb;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Header;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Records a MongoDB Atlas Data API (REST) interaction as a tracked pair — the reusable core the
 * {@link AtlasDataApiTrackingInterceptor} (and any other transport hook) delegates to. Java analog of the
 * .NET {@code AtlasDataApiTrackingMessageHandler} body: classifies the request from its URI + JSON body via
 * {@link AtlasDataApiOperationClassifier}, applies excluded-operation / phase / Summarised-Other / identity
 * gates, and emits the pair on the {@link DependencyCategories#ATLAS_DATA_API} category with the classifier's
 * label and the {@code atlas:///<db>/<coll>} clean URI (the raw request URI + HTTP method at Raw verbosity).
 */
public final class AtlasDataApiTracking {

    private AtlasDataApiTracking() {
    }

    /**
     * Records one Atlas Data API exchange.
     *
     * @param httpMethod      the request method (used as the diagram label at Raw verbosity)
     * @param requestUri      the request URI ({@code /action/{name}} drives classification)
     * @param requestBody     the JSON request body (drives data-source/db/collection/filter extraction)
     * @param requestHeaders  the request headers (filtered by {@code excludedHeaders}; dropped at Summarised)
     * @param responseBody    the response body (dropped at Summarised)
     * @param statusCode      the HTTP response status
     */
    public static void record(AtlasDataApiTrackingOptions options, String httpMethod, URI requestUri,
                              String requestBody, List<Header> requestHeaders, String responseBody,
                              int statusCode) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        AtlasDataApiOperationInfo info = AtlasDataApiOperationClassifier.classify(requestUri, requestBody);
        if (options.excludedOperations().contains(info.operation())) {
            return;
        }
        TrackingVerbosity verbosity = PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
        if (verbosity == TrackingVerbosity.SUMMARISED && info.operation() == AtlasDataApiOperation.OTHER) {
            return; // unrecognised operations are skipped in Summarised mode (matches .NET)
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }

        String label = AtlasDataApiOperationClassifier.getDiagramLabel(info, verbosity);
        boolean raw = verbosity == TrackingVerbosity.RAW;
        Method method = raw ? methodOf(httpMethod) : Method.of(label);
        URI uri = raw ? requestUri : buildCleanUri(info);
        boolean summarised = verbosity == TrackingVerbosity.SUMMARISED;
        String requestContent = summarised ? null : requestBody;
        String responseContent = summarised ? null : responseBody;
        List<Header> headers = summarised ? List.of() : filterHeaders(requestHeaders, options.excludedHeaders());

        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.ATLAS_DATA_API, method, uri, headers, requestContent,
            StatusCode.of(statusCode), responseContent,
            io.kronikol.core.tracking.RequestResponseMetaType.DEFAULT);
    }

    /** The clean diagram URI: {@code atlas:///<db>/<coll>} (omitting absent parts), else {@code atlas:///}. */
    static URI buildCleanUri(AtlasDataApiOperationInfo op) {
        StringBuilder path = new StringBuilder("atlas:///");
        boolean first = true;
        if (op.databaseName() != null) {
            path.append(op.databaseName());
            first = false;
        }
        if (op.collectionName() != null) {
            if (!first) {
                path.append('/');
            }
            path.append(op.collectionName());
        }
        return URI.create(path.toString());
    }

    private static List<Header> filterHeaders(List<Header> headers, Set<String> excluded) {
        if (headers == null || headers.isEmpty()) {
            return List.of();
        }
        return headers.stream().filter(h -> !excluded.contains(h.key())).toList();
    }

    private static Method methodOf(String httpMethod) {
        try {
            return Method.Http.valueOf(httpMethod);
        } catch (IllegalArgumentException notStandard) {
            return Method.of(httpMethod);
        }
    }

    /** Configuration for Atlas Data API tracking. */
    public record AtlasDataApiTrackingOptions(String serviceName, String callerName,
                                              Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity,
                                              boolean trackDuringSetup, boolean trackDuringAction,
                                              TrackingVerbosity setupVerbosity, TrackingVerbosity actionVerbosity,
                                              Set<AtlasDataApiOperation> excludedOperations,
                                              Set<String> excludedHeaders) {

        public AtlasDataApiTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
            excludedOperations = excludedOperations == null ? Set.of() : Set.copyOf(excludedOperations);
            excludedHeaders = excludedHeaders == null ? Set.of() : Set.copyOf(excludedHeaders);
        }

        public static AtlasDataApiTrackingOptions forService(String serviceName) {
            return new AtlasDataApiTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT, true, true, null, null, Set.of(), Set.of());
        }

        public AtlasDataApiTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new AtlasDataApiTrackingOptions(serviceName, callerName, testInfoFetcher, value,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity, excludedOperations,
                excludedHeaders);
        }

        public AtlasDataApiTrackingOptions withTestInfoFetcher(Supplier<TestInfo> value) {
            return new AtlasDataApiTrackingOptions(serviceName, callerName, value, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity, excludedOperations,
                excludedHeaders);
        }

        public AtlasDataApiTrackingOptions withCallerName(String value) {
            return new AtlasDataApiTrackingOptions(serviceName, value, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity, excludedOperations,
                excludedHeaders);
        }

        public AtlasDataApiTrackingOptions withTrackDuringSetup(boolean value) {
            return new AtlasDataApiTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                value, trackDuringAction, setupVerbosity, actionVerbosity, excludedOperations, excludedHeaders);
        }

        public AtlasDataApiTrackingOptions withTrackDuringAction(boolean value) {
            return new AtlasDataApiTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, value, setupVerbosity, actionVerbosity, excludedOperations, excludedHeaders);
        }

        public AtlasDataApiTrackingOptions withSetupVerbosity(TrackingVerbosity value) {
            return new AtlasDataApiTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, value, actionVerbosity, excludedOperations, excludedHeaders);
        }

        public AtlasDataApiTrackingOptions withActionVerbosity(TrackingVerbosity value) {
            return new AtlasDataApiTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, value, excludedOperations, excludedHeaders);
        }

        public AtlasDataApiTrackingOptions withExcludedOperations(Set<AtlasDataApiOperation> value) {
            return new AtlasDataApiTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity, value, excludedHeaders);
        }

        public AtlasDataApiTrackingOptions withExcludedHeaders(Set<String> value) {
            return new AtlasDataApiTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity, excludedOperations, value);
        }
    }
}
