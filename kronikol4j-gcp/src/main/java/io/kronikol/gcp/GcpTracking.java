package io.kronikol.gcp;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Records Google Cloud service operations as tracked interactions (pure recorders). BigQuery /
 * Cloud Storage → database shape; Pub/Sub → queue (event).
 */
public final class GcpTracking {

    private static final URI BIGQUERY_URI = URI.create("gcp://bigquery/");
    private static final URI STORAGE_URI = URI.create("gcp://storage/");
    private static final URI PUBSUB_URI = URI.create("gcp://pubsub/");

    private GcpTracking() {
    }

    public static void bigQuery(GcpTrackingOptions options, String operation, String dataset, String query) {
        // Summarised omits the query payload — only the dataset identity is kept.
        String payload = options.verbosity().includesPayload() && query != null ? query : "";
        record(options, DependencyCategories.BIG_QUERY, operation, BIGQUERY_URI, dataset + ": " + payload);
    }

    public static void storage(GcpTrackingOptions options, String operation, String bucket, String object) {
        record(options, DependencyCategories.BLOB_STORAGE, operation, STORAGE_URI, bucket + "/" + object);
    }

    public static void pubSub(GcpTrackingOptions options, String topic, String message) {
        if (suppressedByPhase(options)) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        // Summarised omits the message payload — only the topic identity is kept.
        String payload = options.verbosity().includesPayload() && message != null ? message : "";
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.MESSAGE_QUEUE, Method.of("PUBLISH"), PUBSUB_URI, null,
            "topic: " + topic + "\n" + payload,
            StatusCode.of("Sent"), null, RequestResponseMetaType.EVENT);
    }

    private static void record(GcpTrackingOptions options, String category, String operation,
                               URI uri, String request) {
        if (suppressedByPhase(options)) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(), category,
            Method.of(operation == null ? "GCP" : operation.toUpperCase(Locale.ROOT)), uri,
            request, StatusCode.of("OK"), null);
    }

    /** Whether the current phase suppresses tracking per the options' {@code trackDuringSetup/Action}. */
    private static boolean suppressedByPhase(GcpTrackingOptions options) {
        return !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction());
    }

    /** Configuration for Google Cloud tracking. */
    public record GcpTrackingOptions(String serviceName, String callerName,
                                     Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity,
                                     boolean trackDuringSetup, boolean trackDuringAction) {

        public GcpTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
        }

        /** Three-arg shape (default verbosity, both phases tracked) — back-compatible. */
        public GcpTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher) {
            this(serviceName, callerName, testInfoFetcher, TrackingVerbosity.DEFAULT, true, true);
        }

        /** Four-arg shape (both phases tracked) — back-compatible. */
        public GcpTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                  TrackingVerbosity verbosity) {
            this(serviceName, callerName, testInfoFetcher, verbosity, true, true);
        }

        public static GcpTrackingOptions forService(String serviceName) {
            return new GcpTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT, true, true);
        }

        /** A copy with the given verbosity (Summarised omits the BigQuery query / Pub/Sub message). */
        public GcpTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new GcpTrackingOptions(serviceName, callerName, testInfoFetcher, value,
                trackDuringSetup, trackDuringAction);
        }

        /** A copy that (does not) track during the Setup phase (the .NET {@code TrackDuringSetup}). */
        public GcpTrackingOptions withTrackDuringSetup(boolean value) {
            return new GcpTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                value, trackDuringAction);
        }

        /** A copy that (does not) track during the Action phase (the .NET {@code TrackDuringAction}). */
        public GcpTrackingOptions withTrackDuringAction(boolean value) {
            return new GcpTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, value);
        }
    }
}
