package io.kronikol.aws;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Amazon DynamoDB requests into typed operations from the {@code X-Amz-Target} header
 * ({@code DynamoDB_<version>.<Operation>}), extracting the table name(s) and PartiQL statement from the
 * request body. Java port of the .NET {@code DynamoDbOperationClassifier}. Pure logic — no AWS SDK dependency.
 */
public final class DynamoDbOperationClassifier {

    private static final Pattern TARGET = Pattern.compile("DynamoDB_\\d+\\.(?<operation>\\w+)");
    private static final Pattern TABLE_NAME = Pattern.compile("\"TableName\"\\s*:\\s*\"(?<table>[^\"]+)\"");
    private static final Pattern STATEMENT = Pattern.compile("\"Statement\"\\s*:\\s*\"(?<stmt>[^\"]+)\"");

    private DynamoDbOperationClassifier() {
    }

    /** Classifies a DynamoDB request from the {@code X-Amz-Target} header value and the request body. */
    public static DynamoDbOperationInfo classify(String xAmzTarget, String requestBody) {
        if (xAmzTarget == null) {
            return new DynamoDbOperationInfo(DynamoDbOperation.OTHER, null);
        }
        Matcher m = TARGET.matcher(xAmzTarget);
        if (!m.find()) {
            return new DynamoDbOperationInfo(DynamoDbOperation.OTHER, null);
        }
        DynamoDbOperation operation = mapOperation(m.group("operation"));

        String tableName = operation == DynamoDbOperation.BATCH_WRITE_ITEM
            || operation == DynamoDbOperation.BATCH_GET_ITEM
            ? extractBatchTableNames(requestBody)
            : extractTableName(requestBody);

        String statement = operation == DynamoDbOperation.EXECUTE_STATEMENT
            || operation == DynamoDbOperation.BATCH_EXECUTE_STATEMENT
            || operation == DynamoDbOperation.EXECUTE_TRANSACTION
            ? extractStatement(requestBody) : null;

        return new DynamoDbOperationInfo(operation, tableName, statement);
    }

    /** The diagram label: the operation name for Detailed/Summarised; null for Raw. */
    public static String getDiagramLabel(DynamoDbOperationInfo op, TrackingVerbosity verbosity) {
        return verbosity == TrackingVerbosity.RAW ? null : op.operation().displayName();
    }

    private static DynamoDbOperation mapOperation(String operationName) {
        return switch (operationName) {
            case "PutItem" -> DynamoDbOperation.PUT_ITEM;
            case "GetItem" -> DynamoDbOperation.GET_ITEM;
            case "UpdateItem" -> DynamoDbOperation.UPDATE_ITEM;
            case "DeleteItem" -> DynamoDbOperation.DELETE_ITEM;
            case "Query" -> DynamoDbOperation.QUERY;
            case "Scan" -> DynamoDbOperation.SCAN;
            case "BatchWriteItem" -> DynamoDbOperation.BATCH_WRITE_ITEM;
            case "BatchGetItem" -> DynamoDbOperation.BATCH_GET_ITEM;
            case "TransactWriteItems" -> DynamoDbOperation.TRANSACT_WRITE_ITEMS;
            case "TransactGetItems" -> DynamoDbOperation.TRANSACT_GET_ITEMS;
            case "CreateTable" -> DynamoDbOperation.CREATE_TABLE;
            case "DeleteTable" -> DynamoDbOperation.DELETE_TABLE;
            case "DescribeTable" -> DynamoDbOperation.DESCRIBE_TABLE;
            case "ListTables" -> DynamoDbOperation.LIST_TABLES;
            case "UpdateTable" -> DynamoDbOperation.UPDATE_TABLE;
            case "ExecuteStatement" -> DynamoDbOperation.EXECUTE_STATEMENT;
            case "BatchExecuteStatement" -> DynamoDbOperation.BATCH_EXECUTE_STATEMENT;
            case "ExecuteTransaction" -> DynamoDbOperation.EXECUTE_TRANSACTION;
            default -> DynamoDbOperation.OTHER;
        };
    }

    private static String extractTableName(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Matcher m = TABLE_NAME.matcher(body);
        return m.find() ? m.group("table") : null;
    }

    private static String extractStatement(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Matcher m = STATEMENT.matcher(body);
        return m.find() ? m.group("stmt") : null;
    }

    /** Batch ops carry table names as the keys of the {@code RequestItems} object; falls back to TableName. */
    private static String extractBatchTableNames(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        int ri = body.indexOf("\"RequestItems\"");
        if (ri >= 0) {
            int brace = body.indexOf('{', ri);
            if (brace >= 0) {
                List<String> tables = topLevelObjectKeys(body, brace);
                if (!tables.isEmpty()) {
                    return String.join(", ", tables);
                }
            }
        }
        return extractTableName(body); // fallback
    }

    /**
     * The immediate (depth-1) object keys of the JSON object starting at {@code openBrace}. A small
     * dependency-free scan (core has no JSON parser) — enough to read {@code RequestItems} table-name keys.
     */
    private static List<String> topLevelObjectKeys(String s, int openBrace) {
        List<String> keys = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        StringBuilder current = null;
        for (int i = openBrace; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++; // skip the escaped character
                } else if (c == '"') {
                    inString = false;
                    if (depth == 1) {
                        int j = i + 1;
                        while (j < s.length() && Character.isWhitespace(s.charAt(j))) {
                            j++;
                        }
                        if (j < s.length() && s.charAt(j) == ':') {
                            keys.add(current.toString());
                        }
                    }
                    current = null;
                } else {
                    current.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    current = new StringBuilder();
                }
                case '{', '[' -> depth++;
                case '}', ']' -> {
                    depth--;
                    if (depth == 0) {
                        return keys;
                    }
                }
                default -> { }
            }
        }
        return keys;
    }
}
