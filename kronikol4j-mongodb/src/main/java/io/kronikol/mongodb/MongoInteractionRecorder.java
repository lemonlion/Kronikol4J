package io.kronikol.mongodb;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.CorrelationKeys;
import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestCorrelationStore;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.PhaseVariant;
import io.kronikol.core.tracking.PhaseVariantExtensions;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonWriterSettings;

/**
 * Builds the request/response {@link RequestResponseLog} pair for a MongoDB command, with parity to the
 * .NET {@code MongoDbTrackingSubscriber}. Two-phase, keyed on the driver's per-command request id:
 * {@link #logStarted} emits the request and stashes a pending entry; {@link #logSucceeded}/{@link #logFailed}
 * emit the matching response. A thin {@code CommandListener} adapter feeds it driver events.
 */
public final class MongoInteractionRecorder {

    private final MongoDbTrackingOptions options;
    private final ConcurrentHashMap<Integer, Pending> pending = new ConcurrentHashMap<>();

    public MongoInteractionRecorder(MongoDbTrackingOptions options) {
        this.options = options;
    }

    private record Pending(TestInfo testInfo, MongoDbOperationInfo opInfo, URI uri, String label,
                           UUID traceId, UUID requestResponseId) {
    }

    /** Handles a command-started event; emits the request half and remembers it by {@code requestId}. */
    public void logStarted(int requestId, String commandName, String databaseName, BsonDocument command) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TrackingVerbosity ev = effectiveVerbosity();
        if (options.ignoredCommands().contains(commandName)) {
            return;
        }
        if (!options.trackGetMore() && commandName.equalsIgnoreCase("getMore")) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        MongoDbOperationInfo opInfo = MongoDbOperationClassifier.classify(commandName, databaseName, command);
        if (options.excludedOperations().contains(opInfo.operation())) {
            return;
        }
        if (ev == TrackingVerbosity.SUMMARISED && opInfo.operation() == MongoDbOperation.OTHER) {
            return;
        }

        URI uri = buildUri(opInfo, ev);
        String label = MongoDbOperationClassifier.getDiagramLabel(opInfo, ev);
        UUID traceId = options.ids().newId();
        UUID requestResponseId = options.ids().newId();
        TestPhase phase = TestPhaseContext.current();
        pending.put(requestId, new Pending(who, opInfo, uri, label, traceId, requestResponseId));

        RequestResponseLog log = RequestResponseLog.builder()
            .testInfo(who).method(Method.of(label)).content(requestContent(command, opInfo, ev))
            .uri(uri).headers(List.of())
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).dependencyCategory(DependencyCategories.MONGO_DB).phase(phase).build();

        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(MongoDbOperationClassifier.getDiagramLabel(opInfo, v)),
                buildUri(opInfo, v), requestContent(command, opInfo, v), List.of(),
                v == TrackingVerbosity.SUMMARISED && opInfo.operation() == MongoDbOperation.OTHER));

        RequestResponseLogger.log(log);
    }

    /** Handles a command-succeeded event; emits the response half (status OK) for {@code requestId}. */
    public void logSucceeded(int requestId, BsonDocument reply) {
        Pending p = pending.remove(requestId);
        if (p == null) {
            return;
        }
        autoCorrelateIfWrite(p);
        TrackingVerbosity ev = effectiveVerbosity();
        emitResponse(p, responseContent(reply, ev), StatusCode.of("OK"));
    }

    /** Handles a command-failed event; emits the response half (status 500) for {@code requestId}. */
    public void logFailed(int requestId, Throwable failure) {
        Pending p = pending.remove(requestId);
        if (p == null) {
            return;
        }
        emitResponse(p, failure != null ? failure.getMessage() : null, StatusCode.of(500));
    }

    private void emitResponse(Pending p, String content, StatusCode status) {
        TestPhase phase = TestPhaseContext.current();
        RequestResponseLog log = RequestResponseLog.builder()
            .testInfo(p.testInfo()).method(Method.of(p.label())).content(content)
            .uri(p.uri()).headers(List.of())
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE).traceId(p.traceId()).requestResponseId(p.requestResponseId())
            .trackingIgnore(false).statusCode(status).dependencyCategory(DependencyCategories.MONGO_DB)
            .phase(phase).build();

        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(MongoDbOperationClassifier.getDiagramLabel(p.opInfo(), v)),
                buildUri(p.opInfo(), v), content, List.of(),
                v == TrackingVerbosity.SUMMARISED && p.opInfo().operation() == MongoDbOperation.OTHER));

        RequestResponseLogger.log(log);
    }

    private TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }

    private String requestContent(BsonDocument command, MongoDbOperationInfo opInfo, TrackingVerbosity ev) {
        return switch (ev) {
            case SUMMARISED -> null;
            case RAW -> command != null ? command.toString() : null;
            default -> options.logFilterText() ? opInfo.filterText() : null;
        };
    }

    private String responseContent(BsonDocument reply, TrackingVerbosity ev) {
        if (ev == TrackingVerbosity.SUMMARISED && !options.logResponseContent()) {
            return null;
        }
        if (ev == TrackingVerbosity.RAW) {
            return reply != null ? reply.toString() : null;
        }
        return extractDetailedResponse(reply);
    }

    private static String extractResponseMetadata(BsonDocument reply) {
        if (reply == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (reply.containsKey("n") && reply.get("n").isInt32()) {
            parts.add("n=" + reply.getInt32("n").getValue());
        }
        if (reply.containsKey("nModified") && reply.get("nModified").isInt32()) {
            parts.add("nModified=" + reply.getInt32("nModified").getValue());
        }
        if (reply.containsKey("nUpserted") && reply.get("nUpserted").isInt32()
            && reply.getInt32("nUpserted").getValue() > 0) {
            parts.add("nUpserted=" + reply.getInt32("nUpserted").getValue());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String extractDetailedResponse(BsonDocument reply) {
        String metadata = extractResponseMetadata(reply);
        if (!options.logResponseContent() || reply == null) {
            return metadata;
        }
        if (reply.containsKey("cursor") && reply.get("cursor").isDocument()) {
            BsonDocument cursor = reply.get("cursor").asDocument();
            if (cursor.containsKey("firstBatch") && cursor.get("firstBatch").isArray()) {
                List<BsonValue> docs = cursor.get("firstBatch").asArray().getValues();
                if (docs.isEmpty()) {
                    return metadata != null ? metadata + "\n0 documents" : "0 documents";
                }
                JsonWriterSettings settings = JsonWriterSettings.builder()
                    .indent(true).indentCharacters("  ").newLineCharacters("\n").build();
                List<String> formatted = new ArrayList<>();
                int limit = Math.min(docs.size(), options.maxResponseDocuments());
                for (int i = 0; i < limit; i++) {
                    String json = docs.get(i).asDocument().toJson(settings);
                    formatted.add("  " + json.replace("\n", "\n  "));
                }
                String docText = "[\n" + String.join(",\n", formatted) + "\n]";
                if (docs.size() > options.maxResponseDocuments()) {
                    docText += "\n... (" + (docs.size() - options.maxResponseDocuments())
                        + " more documents not shown)";
                }
                return metadata != null ? metadata + "\n" + docText : docText;
            }
        }
        return metadata;
    }

    private URI buildUri(MongoDbOperationInfo opInfo, TrackingVerbosity ev) {
        String db = opInfo.databaseName() != null ? opInfo.databaseName() : "unknown";
        String coll = opInfo.collectionName();
        if (ev == TrackingVerbosity.SUMMARISED) {
            return URI.create("mongodb:///" + db);
        }
        return coll != null ? URI.create("mongodb:///" + db + "/" + coll) : URI.create("mongodb:///" + db);
    }

    private void autoCorrelateIfWrite(Pending p) {
        if (!options.autoCorrelateWrites() || p.opInfo().documentId() == null) {
            return;
        }
        MongoDbOperation op = p.opInfo().operation();
        boolean isWrite = op == MongoDbOperation.INSERT || op == MongoDbOperation.UPDATE
            || op == MongoDbOperation.FIND_AND_MODIFY;
        if (!isWrite) {
            return;
        }
        String key = CorrelationKeys.mongo(options.serviceName(), p.opInfo().documentId());
        TestCorrelationStore.correlate(key, p.testInfo().name(), p.testInfo().id());
    }
}
