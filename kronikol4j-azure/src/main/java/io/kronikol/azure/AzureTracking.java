package io.kronikol.azure;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
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
        record(options, DependencyCategories.COSMOS_DB, operation, COSMOS_URI,
            container + ": " + (document == null ? "" : document));
    }

    public static void blob(AzureTrackingOptions options, String operation, String container, String blob) {
        record(options, DependencyCategories.BLOB_STORAGE, operation, BLOB_URI, container + "/" + blob);
    }

    public static void serviceBus(AzureTrackingOptions options, String entity, String message) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.SERVICE_BUS, Method.of("SEND"), SERVICE_BUS_URI, null,
            "entity: " + entity + "\n" + (message == null ? "" : message),
            StatusCode.of("Sent"), null, RequestResponseMetaType.EVENT);
    }

    private static void record(AzureTrackingOptions options, String category, String operation,
                               URI uri, String request) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(), category,
            Method.of(operation == null ? "AZURE" : operation.toUpperCase(Locale.ROOT)), uri,
            request, StatusCode.of("OK"), null);
    }

    /** Configuration for Azure tracking. */
    public record AzureTrackingOptions(String serviceName, String callerName,
                                       Supplier<TestInfo> testInfoFetcher) {
        public static AzureTrackingOptions forService(String serviceName) {
            return new AzureTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null);
        }
    }
}
