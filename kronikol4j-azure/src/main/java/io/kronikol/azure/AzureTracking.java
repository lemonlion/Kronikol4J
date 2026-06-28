package io.kronikol.azure;

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
 * Records Azure service operations as tracked interactions (pure; an Azure SDK pipeline policy
 * delegates to these). Cosmos DB / Blob Storage → database shape; Service Bus → queue (event).
 */
public final class AzureTracking {

    private static final URI COSMOS_URI = URI.create("azure://cosmos/");
    private static final URI BLOB_URI = URI.create("azure://blob/");
    private static final URI SERVICE_BUS_URI = URI.create("azure://servicebus/");

    private AzureTracking() {
    }

    public static void cosmos(AzureTrackingOptions options, String operation, String container, String document) {
        // Summarised omits the document payload — only the container identity is kept.
        String payload = options.verbosity().includesPayload() && document != null ? document : "";
        record(options, DependencyCategories.COSMOS_DB, operation, COSMOS_URI, container + ": " + payload);
    }

    public static void blob(AzureTrackingOptions options, String operation, String container, String blob) {
        record(options, DependencyCategories.BLOB_STORAGE, operation, BLOB_URI, container + "/" + blob);
    }

    public static void serviceBus(AzureTrackingOptions options, String entity, String message) {
        if (suppressedByPhase(options)) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        // Summarised omits the message payload — only the entity identity is kept.
        String payload = options.verbosity().includesPayload() && message != null ? message : "";
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.SERVICE_BUS, Method.of("SEND"), SERVICE_BUS_URI, null,
            "entity: " + entity + "\n" + payload,
            StatusCode.of("Sent"), null, RequestResponseMetaType.EVENT);
    }

    private static void record(AzureTrackingOptions options, String category, String operation,
                               URI uri, String request) {
        if (suppressedByPhase(options)) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(), category,
            Method.of(operation == null ? "AZURE" : operation.toUpperCase(Locale.ROOT)), uri,
            request, StatusCode.of("OK"), null);
    }

    /** Whether the current phase suppresses tracking per the options' {@code trackDuringSetup/Action}. */
    private static boolean suppressedByPhase(AzureTrackingOptions options) {
        return !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction());
    }

    /** Configuration for Azure tracking. */
    public record AzureTrackingOptions(String serviceName, String callerName,
                                       Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity,
                                       boolean trackDuringSetup, boolean trackDuringAction) {

        public AzureTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
        }

        /** Three-arg shape (default verbosity, both phases tracked) — back-compatible. */
        public AzureTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher) {
            this(serviceName, callerName, testInfoFetcher, TrackingVerbosity.DEFAULT, true, true);
        }

        /** Four-arg shape (both phases tracked) — back-compatible. */
        public AzureTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                    TrackingVerbosity verbosity) {
            this(serviceName, callerName, testInfoFetcher, verbosity, true, true);
        }

        public static AzureTrackingOptions forService(String serviceName) {
            return new AzureTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT, true, true);
        }

        /** A copy with the given verbosity (Summarised omits the Cosmos document / Service Bus message). */
        public AzureTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, value,
                trackDuringSetup, trackDuringAction);
        }

        /** A copy that (does not) track during the Setup phase (the .NET {@code TrackDuringSetup}). */
        public AzureTrackingOptions withTrackDuringSetup(boolean value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                value, trackDuringAction);
        }

        /** A copy that (does not) track during the Action phase (the .NET {@code TrackDuringAction}). */
        public AzureTrackingOptions withTrackDuringAction(boolean value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, value);
        }
    }
}
