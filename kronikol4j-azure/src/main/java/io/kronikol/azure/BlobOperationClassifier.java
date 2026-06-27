package io.kronikol.azure;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Azure Blob Storage requests into typed operations from the HTTP method, the
 * {@code /{container}/{blob}} path, and the {@code restype}/{@code comp} query parameters. Java port of the
 * .NET {@code BlobOperationClassifier}. Pure logic — no Azure SDK dependency.
 */
public final class BlobOperationClassifier {

    private static final Pattern BLOB_PATH =
        Pattern.compile("^/(?<container>[^/?]+)(?:/(?<blob>[^?]+))?", Pattern.CASE_INSENSITIVE);

    private BlobOperationClassifier() {
    }

    /** Classifies a Blob Storage request from its HTTP method and URI. */
    public static BlobOperationInfo classify(String httpMethod, URI uri) {
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath();

        Matcher m = BLOB_PATH.matcher(path);
        String container = null;
        String blob = null;
        if (m.find()) {
            container = m.group("container");
            blob = m.group("blob");
        }
        boolean hasBlob = blob != null && !blob.isEmpty();

        Map<String, String> query = queryParams(uri == null ? null : uri.getQuery());
        String restype = query.get("restype");
        boolean isContainer = restype != null && restype.equalsIgnoreCase("container");
        String comp = query.get("comp");
        comp = comp == null ? null : comp.toLowerCase(Locale.ROOT);
        boolean compEmpty = comp == null || comp.isEmpty();

        BlobOperation operation;
        if (method.equals("PUT") && !hasBlob && isContainer && compEmpty) {
            operation = BlobOperation.CREATE_CONTAINER;
        } else if (method.equals("DELETE") && !hasBlob && isContainer) {
            operation = BlobOperation.DELETE_CONTAINER;
        } else if (!hasBlob && isContainer && "list".equals(comp)) {
            operation = BlobOperation.LIST_BLOBS;
        } else if (method.equals("PUT") && hasBlob && "metadata".equals(comp)) {
            operation = BlobOperation.SET_METADATA;
        } else if (method.equals("GET") && hasBlob && "metadata".equals(comp)) {
            operation = BlobOperation.GET_METADATA;
        } else if (method.equals("PUT") && hasBlob && "copy".equals(comp)) {
            operation = BlobOperation.COPY;
        } else if (method.equals("PUT") && hasBlob && "block".equals(comp)) {
            operation = BlobOperation.PUT_BLOCK;
        } else if (method.equals("PUT") && hasBlob && "blocklist".equals(comp)) {
            operation = BlobOperation.PUT_BLOCK_LIST;
        } else if (hasBlob && "lease".equals(comp)) {
            operation = BlobOperation.LEASE;
        } else if (method.equals("PUT") && hasBlob && compEmpty) {
            operation = BlobOperation.UPLOAD;
        } else if (method.equals("GET") && hasBlob && compEmpty) {
            operation = BlobOperation.DOWNLOAD;
        } else if (method.equals("DELETE") && hasBlob && compEmpty) {
            operation = BlobOperation.DELETE;
        } else if (method.equals("HEAD") && hasBlob && compEmpty) {
            operation = BlobOperation.GET_PROPERTIES;
        } else {
            operation = BlobOperation.OTHER;
        }

        return new BlobOperationInfo(operation, nullIfEmpty(container), hasBlob ? blob : null);
    }

    /** The diagram label: the operation name for Detailed/Summarised; null for Raw. */
    public static String getDiagramLabel(BlobOperationInfo op, TrackingVerbosity verbosity) {
        return verbosity == TrackingVerbosity.RAW ? null : op.operation().displayName();
    }

    private static Map<String, String> queryParams(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                result.put(pair.toLowerCase(Locale.ROOT), "");
            } else {
                result.put(pair.substring(0, eq).toLowerCase(Locale.ROOT), pair.substring(eq + 1));
            }
        }
        return result;
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
