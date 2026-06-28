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
import io.kronikol.core.context.CorrelationKeys;
import io.kronikol.core.context.TestCorrelationStore;
import java.util.Locale;
import java.util.function.BiFunction;
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
        String payload = effectiveVerbosity(options).includesPayload() && document != null ? document : "";
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
        String payload = effectiveVerbosity(options).includesPayload() && message != null ? message : "";
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.SERVICE_BUS, Method.of("SEND"), SERVICE_BUS_URI, null,
            "entity: " + entity + "\n" + payload,
            StatusCode.of("Sent"), null, RequestResponseMetaType.EVENT);
    }

    /**
     * Records an Azure Storage Queues REST interaction, classifying the request from its HTTP method + URI via
     * {@link StorageQueueOperationClassifier} — the reusable core an Azure Queue REST interceptor delegates to
     * (the .NET {@code StorageQueueTrackingMessageHandler} analog). Request/response shape (real
     * {@code statusCode}), {@code MessageQueue} category → queue participant; diagram label from the classifier;
     * {@code storagequeue:///<queue>} URI (the raw request URI at Raw verbosity); body honoured per (per-phase)
     * verbosity.
     *
     * @param httpMethod the request method ({@code POST}/{@code GET}/{@code DELETE}/{@code PUT})
     * @param requestUri the request URI (path drives queue / message-id / operation classification)
     * @param body       the request body (or a redacted form); dropped at Summarised verbosity
     * @param statusCode the HTTP response status
     */
    public static void storageQueue(AzureTrackingOptions options, String httpMethod, URI requestUri,
                                    String body, int statusCode) {
        if (suppressedByPhase(options)) {
            return;
        }
        StorageQueueOperationInfo info = StorageQueueOperationClassifier.classify(httpMethod, requestUri);
        TrackingVerbosity verbosity = effectiveVerbosity(options);
        String label = StorageQueueOperationClassifier.getDiagramLabel(info, verbosity);
        URI uri = verbosity == TrackingVerbosity.RAW ? requestUri
            : info.queueName() != null ? URI.create("storagequeue:///" + info.queueName())
            : URI.create("storagequeue:///");
        String content = verbosity.includesPayload() ? body : null;
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.MESSAGE_QUEUE, Method.of(label), uri, content,
            StatusCode.of(statusCode), null);
    }

    /**
     * Records an Azure Cosmos DB REST interaction, classifying the request from its HTTP method + URI + the
     * {@code x-ms-documentdb-isquery}/{@code -is-upsert} header flags via {@link CosmosOperationClassifier} —
     * the reusable core an Azure pipeline policy delegates to (the .NET {@code CosmosTrackingMessageHandler}).
     * Request/response shape on the {@code CosmosDB} category; the clean URI rewrites the original request
     * URI's path to {@code /colls/<coll>[/docs|sprocs/<id>]} (Detailed) or {@code /<coll>} (Summarised),
     * keeping the host (the raw request URI at Raw). When {@code autoCorrelateWrites} is on, a successful
     * Create/Upsert/Replace seeds {@code TestCorrelationStore} keyed by the document id (from the path or the
     * {@code responseBody}'s {@code "id"} field) so background change-feed processing can attribute the test.
     */
    public static void cosmos(AzureTrackingOptions options, String httpMethod, URI requestUri,
                              boolean isQuery, boolean isUpsert, String body, int statusCode, String responseBody) {
        if (suppressedByPhase(options)) {
            return;
        }
        CosmosOperationInfo info = CosmosOperationClassifier.classify(httpMethod, requestUri, isQuery, isUpsert, body);
        TrackingVerbosity verbosity = effectiveVerbosity(options);
        if (verbosity == TrackingVerbosity.SUMMARISED && info.operation() == CosmosOperation.OTHER) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        boolean raw = verbosity == TrackingVerbosity.RAW;
        Method method = raw ? methodOf(httpMethod) : Method.of(CosmosOperationClassifier.getDiagramLabel(info, verbosity));
        URI uri = raw ? requestUri : buildCosmosUri(requestUri, info, verbosity);
        String content = verbosity == TrackingVerbosity.SUMMARISED ? null : body;
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.COSMOS_DB, method, uri, content, StatusCode.of(statusCode), null);
        autoCorrelateCosmosWrite(options, info, statusCode, responseBody, who);
    }

    /** Six-arg overload (no response body / no write-correlation) — back-compatible. */
    public static void cosmos(AzureTrackingOptions options, String httpMethod, URI requestUri,
                              boolean isQuery, boolean isUpsert, String body, int statusCode) {
        cosmos(options, httpMethod, requestUri, isQuery, isUpsert, body, statusCode, null);
    }

    /** Seeds {@code TestCorrelationStore} for a successful Cosmos write, mirroring .NET {@code AutoCorrelateIfWrite}. */
    private static void autoCorrelateCosmosWrite(AzureTrackingOptions options, CosmosOperationInfo info,
                                                 int statusCode, String responseBody, TestInfo who) {
        if (!options.autoCorrelateWrites() || statusCode < 200 || statusCode >= 300) {
            return;
        }
        boolean isWrite = info.operation() == CosmosOperation.CREATE
            || info.operation() == CosmosOperation.UPSERT
            || info.operation() == CosmosOperation.REPLACE;
        if (!isWrite) {
            return;
        }
        String documentId = info.documentId() != null ? info.documentId() : extractJsonId(responseBody);
        if (documentId == null) {
            return;
        }
        String key = options.changeFeedKeyExtractor() != null
            ? options.changeFeedKeyExtractor().apply(options.serviceName(), documentId)
            : CorrelationKeys.cosmos(options.serviceName(), documentId);
        TestCorrelationStore.correlate(key, who.name(), who.id());
    }

    private static final java.util.regex.Pattern JSON_ID =
        java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"(?<id>[^\"]*)\"");

    /** Extracts the top-level {@code "id"} string from a JSON body (the .NET {@code ExtractIdFromResponseContent}). */
    private static String extractJsonId(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher m = JSON_ID.matcher(json);
        return m.find() ? m.group("id") : null;
    }

    /**
     * Records an Azure Blob Storage REST interaction, classifying the request from its HTTP method + URI via
     * {@link BlobOperationClassifier} — the .NET {@code BlobTrackingMessageHandler} analog. Request/response
     * shape on the {@code BlobStorage} category; the clean URI rewrites the path to
     * {@code /<container>[/<blob>]} and strips the query (the raw request URI at Raw).
     */
    public static void blob(AzureTrackingOptions options, String httpMethod, URI requestUri,
                            String body, int statusCode) {
        if (suppressedByPhase(options)) {
            return;
        }
        BlobOperationInfo info = BlobOperationClassifier.classify(httpMethod, requestUri);
        TrackingVerbosity verbosity = effectiveVerbosity(options);
        if (verbosity == TrackingVerbosity.SUMMARISED && info.operation() == BlobOperation.OTHER) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        boolean raw = verbosity == TrackingVerbosity.RAW;
        Method method = raw ? methodOf(httpMethod) : Method.of(BlobOperationClassifier.getDiagramLabel(info, verbosity));
        URI uri = raw ? requestUri : buildBlobUri(requestUri, info);
        String content = verbosity == TrackingVerbosity.SUMMARISED ? null : body;
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.BLOB_STORAGE, method, uri, content, StatusCode.of(statusCode), null);
    }

    /** The Cosmos clean URI: original host + path {@code /colls/<coll>[/docs|sprocs/<id>]} (Detailed) or
     *  {@code /<coll>} (Summarised); the original URI unchanged when the collection is unknown. */
    static URI buildCosmosUri(URI original, CosmosOperationInfo op, TrackingVerbosity verbosity) {
        if (op.collectionName() == null) {
            return original;
        }
        String path;
        if (verbosity == TrackingVerbosity.SUMMARISED) {
            path = "/" + op.collectionName();
        } else {
            StringBuilder sb = new StringBuilder("/colls/").append(op.collectionName());
            if (op.documentId() != null) {
                String resourceType = op.operation() == CosmosOperation.EXEC_STORED_PROC ? "sprocs" : "docs";
                sb.append('/').append(resourceType).append('/').append(op.documentId());
            }
            path = sb.toString();
        }
        return rewrite(original, path, original.getRawQuery());
    }

    /** The Blob clean URI: original host + path {@code /<container>[/<blob>]}, query stripped; the original
     *  URI unchanged when the container is unknown. */
    static URI buildBlobUri(URI original, BlobOperationInfo op) {
        if (op.containerName() == null) {
            return original;
        }
        String path = op.blobName() != null
            ? "/" + op.containerName() + "/" + op.blobName()
            : "/" + op.containerName();
        return rewrite(original, path, null);
    }

    /** Rebuilds {@code original} with a new path/query, preserving scheme + authority (the .NET UriBuilder). */
    private static URI rewrite(URI original, String path, String query) {
        try {
            return new URI(original.getScheme(), original.getAuthority(), path, query, null);
        } catch (Exception e) {
            return original; // malformed — fall back to the original URI rather than fail
        }
    }

    private static Method methodOf(String httpMethod) {
        try {
            return Method.Http.valueOf(httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notStandard) {
            return Method.of(httpMethod == null ? "AZURE" : httpMethod.toUpperCase(Locale.ROOT));
        }
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

    /** The verbosity for the current phase: the per-phase override when set, else the base. */
    private static TrackingVerbosity effectiveVerbosity(AzureTrackingOptions options) {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }

    /** Configuration for Azure tracking. */
    public record AzureTrackingOptions(String serviceName, String callerName,
                                       Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity,
                                       boolean trackDuringSetup, boolean trackDuringAction,
                                       TrackingVerbosity setupVerbosity, TrackingVerbosity actionVerbosity,
                                       boolean autoCorrelateWrites,
                                       BiFunction<String, String, String> changeFeedKeyExtractor) {

        public AzureTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
        }

        /** Eight-arg shape (no Cosmos write-correlation) — back-compatible. */
        public AzureTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                    TrackingVerbosity verbosity, boolean trackDuringSetup,
                                    boolean trackDuringAction, TrackingVerbosity setupVerbosity,
                                    TrackingVerbosity actionVerbosity) {
            this(serviceName, callerName, testInfoFetcher, verbosity, trackDuringSetup, trackDuringAction,
                setupVerbosity, actionVerbosity, false, null);
        }

        /** Three-arg shape (default verbosity, both phases tracked) — back-compatible. */
        public AzureTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher) {
            this(serviceName, callerName, testInfoFetcher, TrackingVerbosity.DEFAULT, true, true, null, null);
        }

        /** Four-arg shape (both phases tracked) — back-compatible. */
        public AzureTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                    TrackingVerbosity verbosity) {
            this(serviceName, callerName, testInfoFetcher, verbosity, true, true, null, null);
        }

        /** Six-arg shape (no per-phase verbosity overrides) — back-compatible. */
        public AzureTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                    TrackingVerbosity verbosity, boolean trackDuringSetup,
                                    boolean trackDuringAction) {
            this(serviceName, callerName, testInfoFetcher, verbosity, trackDuringSetup, trackDuringAction,
                null, null);
        }

        public static AzureTrackingOptions forService(String serviceName) {
            return new AzureTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT, true, true, null, null);
        }

        /** A copy with the given verbosity (Summarised omits the Cosmos document / Service Bus message). */
        public AzureTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, value,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity,
                autoCorrelateWrites, changeFeedKeyExtractor);
        }

        /** A copy with the given test-identity fetcher (the .NET {@code CurrentTestInfoFetcher}). */
        public AzureTrackingOptions withTestInfoFetcher(Supplier<TestInfo> value) {
            return new AzureTrackingOptions(serviceName, callerName, value, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity,
                autoCorrelateWrites, changeFeedKeyExtractor);
        }

        /** A copy that (does not) track during the Setup phase (the .NET {@code TrackDuringSetup}). */
        public AzureTrackingOptions withTrackDuringSetup(boolean value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                value, trackDuringAction, setupVerbosity, actionVerbosity,
                autoCorrelateWrites, changeFeedKeyExtractor);
        }

        /** A copy that (does not) track during the Action phase (the .NET {@code TrackDuringAction}). */
        public AzureTrackingOptions withTrackDuringAction(boolean value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, value, setupVerbosity, actionVerbosity,
                autoCorrelateWrites, changeFeedKeyExtractor);
        }

        /** A copy with a Setup-phase verbosity override (the .NET {@code SetupVerbosity}; {@code null} = base). */
        public AzureTrackingOptions withSetupVerbosity(TrackingVerbosity value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, value, actionVerbosity,
                autoCorrelateWrites, changeFeedKeyExtractor);
        }

        /** A copy with an Action-phase verbosity override (the .NET {@code ActionVerbosity}; {@code null} = base). */
        public AzureTrackingOptions withActionVerbosity(TrackingVerbosity value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, value,
                autoCorrelateWrites, changeFeedKeyExtractor);
        }

        /** A copy that seeds {@code TestCorrelationStore} for successful Cosmos writes (the .NET
         *  {@code AutoCorrelateWrites}) — so background change-feed processing can attribute to the test. */
        public AzureTrackingOptions withAutoCorrelateWrites(boolean value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity,
                value, changeFeedKeyExtractor);
        }

        /** A copy with a custom correlation-key extractor {@code (serviceName, documentId) -> key} (the .NET
         *  {@code ChangeFeedKeyExtractor}; {@code null} = the default {@code CorrelationKeys.cosmos}). */
        public AzureTrackingOptions withChangeFeedKeyExtractor(BiFunction<String, String, String> value) {
            return new AzureTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity,
                autoCorrelateWrites, value);
        }
    }
}
