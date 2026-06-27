package io.kronikol.mongodb;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Classifies MongoDB commands into typed operations and extracts metadata (collection, filter, document
 * count/id, pipeline stages, GridFS) from the BSON command document. Java port of the .NET
 * {@code MongoDbOperationClassifier}. The {@code org.bson} dependency is {@code compileOnly}.
 */
public final class MongoDbOperationClassifier {

    private MongoDbOperationClassifier() {
    }

    public static MongoDbOperationInfo classify(String commandName, String databaseName, BsonDocument command) {
        MongoDbOperation operation = switch (commandName.toLowerCase(Locale.ROOT)) {
            case "find" -> MongoDbOperation.FIND;
            case "insert" -> MongoDbOperation.INSERT;
            case "update" -> MongoDbOperation.UPDATE;
            case "delete" -> MongoDbOperation.DELETE;
            case "aggregate" -> detectChangeStream(command) ? MongoDbOperation.WATCH : MongoDbOperation.AGGREGATE;
            case "count", "countdocuments" -> MongoDbOperation.COUNT;
            case "findandmodify" -> MongoDbOperation.FIND_AND_MODIFY;
            case "distinct" -> MongoDbOperation.DISTINCT;
            case "bulkwrite" -> MongoDbOperation.BULK_WRITE;
            case "createindexes" -> MongoDbOperation.CREATE_INDEX;
            case "dropindexes" -> MongoDbOperation.DROP_INDEX;
            case "create" -> MongoDbOperation.CREATE_COLLECTION;
            case "drop" -> MongoDbOperation.DROP_COLLECTION;
            case "listcollections" -> MongoDbOperation.LIST_COLLECTIONS;
            case "listdatabases" -> MongoDbOperation.LIST_DATABASES;
            case "getmore" -> MongoDbOperation.GET_MORE;
            case "mapreduce" -> MongoDbOperation.MAP_REDUCE;
            case "committransaction" -> MongoDbOperation.COMMIT_TRANSACTION;
            case "aborttransaction" -> MongoDbOperation.ABORT_TRANSACTION;
            case "dropdatabase" -> MongoDbOperation.DROP_DATABASE;
            case "renamecollection" -> MongoDbOperation.RENAME_COLLECTION;
            case "listindexes" -> MongoDbOperation.LIST_INDEXES;
            case "serverstatus" -> MongoDbOperation.SERVER_STATUS;
            case "dbstats" -> MongoDbOperation.DB_STATS;
            case "collstats" -> MongoDbOperation.COLL_STATS;
            default -> MongoDbOperation.OTHER;
        };

        String collectionName = extractCollectionName(commandName, command);
        return new MongoDbOperationInfo(operation, databaseName, collectionName,
            extractFilter(command), extractDocumentCount(operation, command),
            extractDocumentId(operation, command), extractPipelineStages(operation, command),
            detectGridFs(collectionName));
    }

    public static String getDiagramLabel(MongoDbOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.operation().displayName() + " " + op.databaseName() + "." + op.collectionName()
                + (op.filterText() != null ? " filter=" + op.filterText() : "");
            case DETAILED -> detailedLabel(op);
            case SUMMARISED -> op.operation().displayName();
        };
    }

    private static String detailedLabel(MongoDbOperationInfo op) {
        if (op.collectionName() == null) {
            return op.operation().displayName();
        }
        String name = op.operation().displayName();
        if (op.documentCount() != null && op.documentCount() > 1) {
            name = name + " (×" + op.documentCount() + ")"; // ×N
        }
        if (op.pipelineStages() != null) {
            name = name + " (" + op.pipelineStages() + ")";
        }
        String label = name + " " + directionalArrow(op.operation()) + " " + op.collectionName();
        return op.isGridFs() ? label + " (GridFS)" : label;
    }

    private static String directionalArrow(MongoDbOperation operation) {
        return switch (operation) {
            case FIND, AGGREGATE, WATCH, COUNT, DISTINCT, GET_MORE, MAP_REDUCE, LIST_INDEXES -> "←"; // ←
            case FIND_AND_MODIFY -> "↔"; // ↔
            default -> "→"; // → (writes / schema / everything else)
        };
    }

    private static String extractCollectionName(String commandName, BsonDocument command) {
        if (command == null) {
            return null;
        }
        for (Map.Entry<String, BsonValue> element : command.entrySet()) {
            if (element.getKey().equalsIgnoreCase(commandName) && element.getValue().isString()) {
                return element.getValue().asString().getValue();
            }
        }
        return null;
    }

    private static String extractFilter(BsonDocument command) {
        if (command == null || !command.containsKey("filter")) {
            return null;
        }
        return command.get("filter").toString();
    }

    private static boolean detectChangeStream(BsonDocument command) {
        BsonArray pipeline = pipelineArray(command);
        if (pipeline == null || pipeline.isEmpty()) {
            return false;
        }
        BsonValue first = pipeline.get(0);
        return first.isDocument() && first.asDocument().containsKey("$changeStream");
    }

    private static Integer extractDocumentCount(MongoDbOperation operation, BsonDocument command) {
        if (command == null) {
            return null;
        }
        if (operation == MongoDbOperation.INSERT && command.containsKey("documents")
            && command.get("documents").isArray()) {
            return command.get("documents").asArray().size();
        }
        return null;
    }

    private static String extractDocumentId(MongoDbOperation operation, BsonDocument command) {
        if (command == null) {
            return null;
        }
        if (command.containsKey("filter") && command.get("filter").isDocument()) {
            BsonDocument filter = command.get("filter").asDocument();
            if (filter.size() == 1 && filter.containsKey("_id")) {
                return plainValue(filter.get("_id"));
            }
        }
        if (operation == MongoDbOperation.DELETE && command.containsKey("deletes")
            && command.get("deletes").isArray()) {
            BsonArray deletes = command.get("deletes").asArray();
            if (deletes.size() == 1 && deletes.get(0).isDocument()) {
                BsonDocument single = deletes.get(0).asDocument();
                if (single.containsKey("q") && single.get("q").isDocument()) {
                    BsonDocument q = single.get("q").asDocument();
                    if (q.size() == 1 && q.containsKey("_id")) {
                        return plainValue(q.get("_id"));
                    }
                }
            }
        }
        return null;
    }

    /**
     * The natural string of a BSON scalar, matching .NET {@code BsonValue.ToString()} (e.g. a string id
     * yields its raw value, an ObjectId its hex) rather than Java's {@code BsonString{value='…'}} debug form.
     * This keeps the extracted document id — and the correlation key built from it — identical across runtimes.
     */
    private static String plainValue(BsonValue v) {
        if (v.isString()) {
            return v.asString().getValue();
        }
        if (v.isInt32()) {
            return Integer.toString(v.asInt32().getValue());
        }
        if (v.isInt64()) {
            return Long.toString(v.asInt64().getValue());
        }
        if (v.isObjectId()) {
            return v.asObjectId().getValue().toHexString();
        }
        if (v.isDouble()) {
            return Double.toString(v.asDouble().getValue());
        }
        if (v.isBoolean()) {
            return Boolean.toString(v.asBoolean().getValue());
        }
        return v.toString();
    }

    private static String extractPipelineStages(MongoDbOperation operation, BsonDocument command) {
        if (operation != MongoDbOperation.AGGREGATE && operation != MongoDbOperation.WATCH) {
            return null;
        }
        BsonArray pipeline = pipelineArray(command);
        if (pipeline == null || pipeline.isEmpty()) {
            return null;
        }
        List<String> stages = new ArrayList<>();
        for (BsonValue stage : pipeline) {
            if (stage.isDocument()) {
                BsonDocument doc = stage.asDocument();
                if (!doc.isEmpty()) {
                    stages.add(doc.keySet().iterator().next());
                }
            }
        }
        return stages.isEmpty() ? null : String.join(", ", stages);
    }

    private static BsonArray pipelineArray(BsonDocument command) {
        if (command == null || !command.containsKey("pipeline")) {
            return null;
        }
        BsonValue pipeline = command.get("pipeline");
        return pipeline.isArray() ? pipeline.asArray() : null;
    }

    private static boolean detectGridFs(String collectionName) {
        return collectionName != null
            && (collectionName.endsWith(".files") || collectionName.endsWith(".chunks"));
    }
}
