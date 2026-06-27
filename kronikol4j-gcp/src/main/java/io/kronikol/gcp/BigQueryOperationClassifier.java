package io.kronikol.gcp;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Google BigQuery REST requests into typed operations from the HTTP method and the
 * {@code /bigquery/v2/projects/{project}/…} resource path (datasets / tables / jobs / queries). Java port of
 * the .NET {@code BigQueryOperationClassifier}. Pure logic — no BigQuery SDK dependency.
 */
public final class BigQueryOperationClassifier {

    private static final Pattern PATH = Pattern.compile(
        "^/(?:upload/)?bigquery/v2/projects/(?<project>[^/]+)/(?<rest>.+)$", Pattern.CASE_INSENSITIVE);

    private BigQueryOperationClassifier() {
    }

    /** Classifies a BigQuery request from its HTTP method and URI. */
    public static BigQueryOperationInfo classify(String httpMethod, URI uri) {
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath();

        Matcher m = PATH.matcher(path);
        if (!m.find()) {
            return new BigQueryOperationInfo(BigQueryOperation.OTHER, null, null, null, null);
        }
        String project = m.group("project");
        String[] segments = m.group("rest").split("/");
        // Drop empty segments (matching .NET RemoveEmptyEntries).
        segments = java.util.Arrays.stream(segments).filter(s -> !s.isEmpty()).toArray(String[]::new);

        if (segments.length == 0) {
            return new BigQueryOperationInfo(BigQueryOperation.OTHER, null, null, project, null);
        }
        return switch (segments[0].toLowerCase(Locale.ROOT)) {
            case "queries" -> classifyQuery(segments, project);
            case "datasets" -> classifyDataset(method, segments, project);
            case "jobs" -> classifyJob(method, segments, project);
            default -> new BigQueryOperationInfo(BigQueryOperation.OTHER, null, null, project, null);
        };
    }

    /** The diagram label: the operation name for Detailed/Summarised; null for Raw. */
    public static String getDiagramLabel(BigQueryOperationInfo op, TrackingVerbosity verbosity) {
        return verbosity == TrackingVerbosity.RAW ? null : op.operation().displayName();
    }

    private static BigQueryOperationInfo classifyQuery(String[] segments, String project) {
        String resourceName = segments.length > 1 ? segments[1] : null;
        return new BigQueryOperationInfo(BigQueryOperation.QUERY, "query", resourceName, project, null);
    }

    private static BigQueryOperationInfo classifyDataset(String method, String[] segments, String project) {
        if (segments.length == 1) {
            BigQueryOperation op = method.equals("POST") ? BigQueryOperation.CREATE : BigQueryOperation.LIST;
            return new BigQueryOperationInfo(op, "dataset", null, project, null);
        }
        String datasetId = segments[1];
        if (segments.length == 2) {
            return new BigQueryOperationInfo(crudOp(method), "dataset", datasetId, project, datasetId);
        }
        return switch (segments[2].toLowerCase(Locale.ROOT)) {
            case "tables" -> classifyTable(method, segments, project, datasetId);
            case "models" -> classifySubResource(method, segments, project, datasetId, "model", 3);
            case "routines" -> classifySubResource(method, segments, project, datasetId, "routine", 3);
            default -> new BigQueryOperationInfo(BigQueryOperation.OTHER, null, null, project, datasetId);
        };
    }

    private static BigQueryOperationInfo classifyTable(String method, String[] segments, String project,
                                                       String datasetId) {
        if (segments.length == 3) {
            BigQueryOperation op = method.equals("POST") ? BigQueryOperation.CREATE : BigQueryOperation.LIST;
            return new BigQueryOperationInfo(op, "table", null, project, datasetId);
        }
        String tableId = segments[3];
        if (segments.length > 4 && segments[4].equalsIgnoreCase("insertAll")) {
            return new BigQueryOperationInfo(BigQueryOperation.INSERT, "table", tableId, project, datasetId);
        }
        if (segments.length > 4 && segments[4].equalsIgnoreCase("data")) {
            return new BigQueryOperationInfo(BigQueryOperation.LIST, "tabledata", tableId, project, datasetId);
        }
        return new BigQueryOperationInfo(crudOp(method), "table", tableId, project, datasetId);
    }

    private static BigQueryOperationInfo classifySubResource(String method, String[] segments, String project,
                                                             String datasetId, String resourceType, int idx) {
        if (segments.length <= idx) {
            BigQueryOperation op = method.equals("POST") ? BigQueryOperation.CREATE : BigQueryOperation.LIST;
            return new BigQueryOperationInfo(op, resourceType, null, project, datasetId);
        }
        BigQueryOperation op = switch (method) {
            case "DELETE" -> BigQueryOperation.DELETE;
            case "PUT", "PATCH" -> BigQueryOperation.UPDATE;
            case "POST" -> BigQueryOperation.CREATE;
            default -> BigQueryOperation.READ;
        };
        return new BigQueryOperationInfo(op, resourceType, segments[idx], project, datasetId);
    }

    private static BigQueryOperationInfo classifyJob(String method, String[] segments, String project) {
        if (segments.length == 1) {
            BigQueryOperation op = method.equals("POST") ? BigQueryOperation.CREATE : BigQueryOperation.LIST;
            return new BigQueryOperationInfo(op, "job", null, project, null);
        }
        String jobId = segments[1];
        if (segments.length > 2 && segments[2].equalsIgnoreCase("cancel")) {
            return new BigQueryOperationInfo(BigQueryOperation.CANCEL, "job", jobId, project, null);
        }
        if (segments.length > 2 && segments[2].equalsIgnoreCase("delete")) {
            return new BigQueryOperationInfo(BigQueryOperation.DELETE, "job", jobId, project, null);
        }
        BigQueryOperation op = method.equals("DELETE") ? BigQueryOperation.DELETE : BigQueryOperation.READ;
        return new BigQueryOperationInfo(op, "job", jobId, project, null);
    }

    /** DELETE → Delete, PUT/PATCH → Update, else Read (the dataset/table CRUD default). */
    private static BigQueryOperation crudOp(String method) {
        return switch (method) {
            case "DELETE" -> BigQueryOperation.DELETE;
            case "PUT", "PATCH" -> BigQueryOperation.UPDATE;
            default -> BigQueryOperation.READ;
        };
    }
}
