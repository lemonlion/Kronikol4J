package io.kronikol.azure;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Azure Cosmos DB requests into typed operations from the HTTP method, the resource path
 * ({@code /dbs/…/colls/…/docs/…}), and the {@code x-ms-documentdb-isquery} / {@code x-ms-documentdb-is-upsert}
 * headers. Java port of the .NET {@code CosmosOperationClassifier}. Pure logic — no Cosmos SDK dependency.
 */
public final class CosmosOperationClassifier {

    // Named and _rid-encoded resource paths: /dbs/{db}/colls/{coll}/{resourceType}/{resourceId}
    private static final Pattern COSMOS_PATH = Pattern.compile(
        "^/(?:dbs/(?<db>[^/]+))?"
            + "(?:/colls/(?<coll>[^/]+))?"
            + "(?:/(?<resourceType>docs|sprocs|triggers|udfs|pkranges)(?:/(?<resourceId>[^/]+))?)?",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern QUERY_TEXT =
        Pattern.compile("\"query\"\\s*:\\s*\"(?<q>(?:\\\\.|[^\"\\\\])*)\"");

    private CosmosOperationClassifier() {
    }

    /**
     * Classifies a Cosmos request. {@code isQuery}/{@code isUpsert} are the {@code x-ms-documentdb-isquery} /
     * {@code -is-upsert} header flags; {@code requestBody} supplies the query text for queries.
     */
    public static CosmosOperationInfo classify(String httpMethod, URI uri, boolean isQuery, boolean isUpsert,
                                               String requestBody) {
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath();

        Matcher m = COSMOS_PATH.matcher(path);
        String db = null;
        String coll = null;
        String resourceType = "";
        String resourceId = null;
        if (m.find()) {
            db = m.group("db");
            coll = m.group("coll");
            resourceType = lower(m.group("resourceType"));
            resourceId = m.group("resourceId");
        }
        boolean hasResourceId = resourceId != null && !resourceId.isEmpty();
        boolean docs = resourceType.equals("docs");
        boolean sprocs = resourceType.equals("sprocs");

        CosmosOperation operation;
        if (method.equals("POST") && docs && !hasResourceId && !isQuery && !isUpsert) {
            operation = CosmosOperation.CREATE;
        } else if (method.equals("POST") && docs && !hasResourceId && !isQuery && isUpsert) {
            operation = CosmosOperation.UPSERT;
        } else if (method.equals("POST") && docs && !hasResourceId && isQuery) {
            operation = CosmosOperation.QUERY;
        } else if (method.equals("GET") && docs && hasResourceId) {
            operation = CosmosOperation.READ;
        } else if (method.equals("GET") && docs) {
            operation = CosmosOperation.LIST;
        } else if (method.equals("PUT") && docs && hasResourceId) {
            operation = CosmosOperation.REPLACE;
        } else if (method.equals("PATCH") && docs && hasResourceId) {
            operation = CosmosOperation.PATCH;
        } else if (method.equals("DELETE") && docs && hasResourceId) {
            operation = CosmosOperation.DELETE;
        } else if (method.equals("POST") && sprocs && hasResourceId) {
            operation = CosmosOperation.EXEC_STORED_PROC;
        } else if (method.equals("POST") && docs && hasResourceId) {
            operation = CosmosOperation.BATCH;
        } else {
            operation = CosmosOperation.OTHER;
        }

        String queryText = operation == CosmosOperation.QUERY ? extractQueryText(requestBody) : null;
        return new CosmosOperationInfo(operation, nullIfEmpty(db), nullIfEmpty(coll),
            hasResourceId ? resourceId : null, queryText);
    }

    /** The diagram label: the operation name for Detailed/Summarised; null for Raw. */
    public static String getDiagramLabel(CosmosOperationInfo op, TrackingVerbosity verbosity) {
        return verbosity == TrackingVerbosity.RAW ? null : op.operation().displayName();
    }

    private static String extractQueryText(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Matcher m = QUERY_TEXT.matcher(body);
        if (!m.find()) {
            return null;
        }
        // Minimal JSON unescaping of the captured query string.
        return m.group("q").replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
