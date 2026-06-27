package io.kronikol.elasticsearch;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Elasticsearch REST requests into typed operations from the HTTP method + URL path. Java port of
 * the .NET {@code ElasticsearchOperationClassifier}. Pure logic (no ES SDK dependency) — the SDK callback
 * hook that feeds it is separate.
 */
public final class ElasticsearchOperationClassifier {

    private static final Pattern DOC_WITH_ID = Pattern.compile("^/(?<index>[^/_][^/]*)/_doc/(?<id>[^/?]+)");
    private static final Pattern DOC_WITHOUT_ID = Pattern.compile("^/(?<index>[^/_][^/]*)/_doc/?$");
    private static final Pattern UPDATE_DOC = Pattern.compile("^/(?<index>[^/_][^/]*)/_update/(?<id>[^/?]+)");
    private static final Pattern INDEX_ACTION = Pattern.compile(
        "^/(?<index>[^/_][^/]*)/_(?<action>search|count|bulk|delete_by_query|update_by_query|mapping|refresh)/?");
    private static final Pattern INDEX_ONLY = Pattern.compile("^/(?<index>[^/_][^/]*)/?$");

    private ElasticsearchOperationClassifier() {
    }

    /** Classifies a request from its HTTP method and URI. */
    public static ElasticsearchOperationInfo classify(String httpMethod, URI uri) {
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        // Cluster-level operations.
        if (path.startsWith("/_cluster/health")) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.CLUSTER_HEALTH, null);
        }
        if (path.startsWith("/_cat/")) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.CAT_APIS, null);
        }
        if (path.startsWith("/_msearch")) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.MULTI_SEARCH, null);
        }
        if (path.equals("/_bulk")) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.BULK, null);
        }
        if (path.startsWith("/_index_template/")) {
            return new ElasticsearchOperationInfo(method.equals("PUT")
                ? ElasticsearchOperation.PUT_INDEX_TEMPLATE
                : ElasticsearchOperation.GET_INDEX_TEMPLATE, null);
        }
        if (path.equals("/_aliases")) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.UPDATE_ALIASES, null);
        }
        if (path.equals("/_reindex")) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.REINDEX, null);
        }
        if (path.contains("/_scroll") || path.contains("/_search/scroll")) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.SCROLL, null);
        }

        // Document with id: /{index}/_doc/{id}
        Matcher docWithId = DOC_WITH_ID.matcher(path);
        if (docWithId.find()) {
            String index = docWithId.group("index");
            String id = docWithId.group("id");
            ElasticsearchOperation op = switch (method) {
                case "GET" -> ElasticsearchOperation.GET_DOCUMENT;
                case "DELETE" -> ElasticsearchOperation.DELETE_DOCUMENT;
                case "PUT" -> ElasticsearchOperation.INDEX_DOCUMENT;
                default -> ElasticsearchOperation.OTHER;
            };
            return new ElasticsearchOperationInfo(op, index, id);
        }

        // Document without id: POST /{index}/_doc
        Matcher docNoId = DOC_WITHOUT_ID.matcher(path);
        if (docNoId.find()) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.INDEX_DOCUMENT, docNoId.group("index"));
        }

        // /{index}/_update/{id}
        Matcher update = UPDATE_DOC.matcher(path);
        if (update.find()) {
            return new ElasticsearchOperationInfo(ElasticsearchOperation.UPDATE_DOCUMENT,
                update.group("index"), update.group("id"));
        }

        // Index-level actions: /{index}/_search, /_count, /_bulk, etc.
        Matcher action = INDEX_ACTION.matcher(path);
        if (action.find()) {
            String index = action.group("index");
            return switch (action.group("action")) {
                case "search" -> new ElasticsearchOperationInfo(ElasticsearchOperation.SEARCH, index);
                case "count" -> new ElasticsearchOperationInfo(ElasticsearchOperation.COUNT, index);
                case "bulk" -> new ElasticsearchOperationInfo(ElasticsearchOperation.BULK, index);
                case "delete_by_query" ->
                    new ElasticsearchOperationInfo(ElasticsearchOperation.DELETE_BY_QUERY, index);
                case "update_by_query" ->
                    new ElasticsearchOperationInfo(ElasticsearchOperation.UPDATE_BY_QUERY, index);
                case "mapping" -> new ElasticsearchOperationInfo(method.equals("PUT")
                    ? ElasticsearchOperation.PUT_MAPPING
                    : method.equals("GET") ? ElasticsearchOperation.GET_MAPPING : ElasticsearchOperation.OTHER,
                    index);
                case "refresh" -> new ElasticsearchOperationInfo(ElasticsearchOperation.REFRESH, index);
                default -> new ElasticsearchOperationInfo(ElasticsearchOperation.OTHER, index);
            };
        }

        // Index-only: PUT/DELETE/HEAD/GET /{index}
        Matcher indexOnly = INDEX_ONLY.matcher(path);
        if (indexOnly.find()) {
            String index = indexOnly.group("index");
            ElasticsearchOperation op = switch (method) {
                case "PUT" -> ElasticsearchOperation.CREATE_INDEX;
                case "DELETE" -> ElasticsearchOperation.DELETE_INDEX;
                case "HEAD" -> ElasticsearchOperation.INDEX_EXISTS;
                case "GET" -> ElasticsearchOperation.GET_MAPPING;
                default -> ElasticsearchOperation.OTHER;
            };
            return new ElasticsearchOperationInfo(op, index);
        }

        return new ElasticsearchOperationInfo(ElasticsearchOperation.OTHER, null);
    }

    /** The diagram label for a classified operation at the given verbosity. */
    public static String getDiagramLabel(ElasticsearchOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.operation().displayName() + " /" + op.indexName()
                + (op.documentId() != null ? "/" + op.documentId() : "");
            case DETAILED -> switch (op.operation()) {
                case INDEX_DOCUMENT -> "Index → " + op.indexName();
                case GET_DOCUMENT -> "Get ← " + op.indexName();
                case DELETE_DOCUMENT -> "Delete " + op.indexName();
                case UPDATE_DOCUMENT -> "Update " + op.indexName();
                case SEARCH -> "Search → " + op.indexName();
                case MULTI_SEARCH -> "MultiSearch";
                case COUNT -> "Count " + op.indexName();
                case BULK -> "Bulk → " + (op.indexName() != null ? op.indexName() : "multi");
                case CREATE_INDEX -> "CreateIndex " + op.indexName();
                case DELETE_INDEX -> "DeleteIndex " + op.indexName();
                default -> op.operation().displayName();
            };
            case SUMMARISED -> switch (op.operation()) {
                case INDEX_DOCUMENT -> "Index";
                case GET_DOCUMENT -> "Get";
                case DELETE_DOCUMENT -> "Delete";
                case UPDATE_DOCUMENT -> "Update";
                case SEARCH -> "Search";
                case BULK -> "Bulk";
                default -> op.operation().displayName();
            };
        };
    }

    /** The diagram URI for a classified operation; Raw uses {@code rawUri} when provided. */
    public static URI buildUri(ElasticsearchOperationInfo op, TrackingVerbosity verbosity, URI rawUri) {
        return switch (verbosity) {
            case RAW -> rawUri != null ? rawUri
                : URI.create("elasticsearch:///" + (op.indexName() != null ? op.indexName() : "cluster"));
            case SUMMARISED -> URI.create("elasticsearch:///");
            default -> URI.create("elasticsearch:///" + (op.indexName() != null ? op.indexName() : "cluster"));
        };
    }
}
