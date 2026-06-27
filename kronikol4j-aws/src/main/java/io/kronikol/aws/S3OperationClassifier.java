package io.kronikol.aws;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Amazon S3 requests into typed operations from the HTTP method, URL (path-style or
 * virtual-hosted-style), query parameters, and the {@code x-amz-copy-source} header. Java port of the .NET
 * {@code S3OperationClassifier}. Pure logic — no AWS SDK dependency.
 */
public final class S3OperationClassifier {

    // Path-style: /{bucket}/{key}
    private static final Pattern PATH_STYLE =
        Pattern.compile("^/(?<bucket>[^/?]+)(?:/(?<key>.+?))?(?:\\?.*)?$", Pattern.CASE_INSENSITIVE);
    // Virtual-hosted-style: {bucket}.s3[.-]…
    private static final Pattern VIRTUAL_HOSTED =
        Pattern.compile("^(?<bucket>[^.]+)\\.s3[.-]", Pattern.CASE_INSENSITIVE);

    private S3OperationClassifier() {
    }

    /**
     * Classifies an S3 request. {@code hasCopySource} is whether the {@code x-amz-copy-source} header is
     * present (distinguishes CopyObject from PutObject).
     */
    public static S3OperationInfo classify(String httpMethod, URI uri, boolean hasCopySource) {
        if (uri == null) {
            return new S3OperationInfo(S3Operation.OTHER, null);
        }
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost();
        String path = uri.getPath() == null ? "" : uri.getPath();

        String bucket;
        String key;

        Matcher virtualMatch = VIRTUAL_HOSTED.matcher(host);
        if (virtualMatch.find()) {
            // Virtual-hosted-style: bucket is in the host, the path IS the key.
            bucket = virtualMatch.group("bucket");
            key = path.length() > 1 ? path.substring(1) : null;
            key = nullIfEmpty(key);
        } else {
            Matcher pathMatch = PATH_STYLE.matcher(path);
            if (!pathMatch.find() || isEmpty(pathMatch.group("bucket"))) {
                return new S3OperationInfo(S3Operation.LIST_BUCKETS, null);
            }
            bucket = pathMatch.group("bucket");
            key = nullIfEmpty(pathMatch.group("key"));
        }

        Set<String> q = queryKeys(uri.getQuery());
        boolean hasKey = key != null;

        S3Operation operation = classifyOperation(method, hasKey, hasCopySource, q);
        return new S3OperationInfo(operation, nullIfEmpty(bucket), key);
    }

    private static S3Operation classifyOperation(String method, boolean hasKey, boolean copy, Set<String> q) {
        if (hasKey) {
            if (method.equals("PUT") && copy) {
                return S3Operation.COPY_OBJECT;
            }
            if (method.equals("PUT") && !copy && q.contains("partnumber") && q.contains("uploadid")) {
                return S3Operation.UPLOAD_PART;
            }
            if (method.equals("PUT") && !copy && q.contains("tagging")) {
                return S3Operation.PUT_OBJECT_TAGGING;
            }
            if (method.equals("GET") && q.contains("tagging")) {
                return S3Operation.GET_OBJECT_TAGGING;
            }
            if (method.equals("DELETE") && q.contains("tagging")) {
                return S3Operation.DELETE_OBJECT_TAGGING;
            }
            if (method.equals("POST") && q.contains("uploads")) {
                return S3Operation.CREATE_MULTIPART_UPLOAD;
            }
            if (method.equals("POST") && q.contains("uploadid")) {
                return S3Operation.COMPLETE_MULTIPART_UPLOAD;
            }
            if (method.equals("DELETE") && q.contains("uploadid")) {
                return S3Operation.ABORT_MULTIPART_UPLOAD;
            }
            return switch (method) {
                case "PUT" -> S3Operation.PUT_OBJECT; // !copy (copy handled above)
                case "GET" -> S3Operation.GET_OBJECT;
                case "DELETE" -> S3Operation.DELETE_OBJECT;
                case "HEAD" -> S3Operation.HEAD_OBJECT;
                default -> S3Operation.OTHER;
            };
        }
        // Bucket-level (no key).
        if (method.equals("GET") && q.contains("list-type")) {
            return S3Operation.LIST_OBJECTS_V2;
        }
        if (method.equals("GET") && q.contains("versions")) {
            return S3Operation.LIST_OBJECT_VERSIONS;
        }
        if (method.equals("GET") && q.contains("location")) {
            return S3Operation.GET_BUCKET_LOCATION;
        }
        if (method.equals("POST") && q.contains("delete")) {
            return S3Operation.DELETE_OBJECTS;
        }
        return switch (method) {
            case "PUT" -> S3Operation.CREATE_BUCKET;
            case "DELETE" -> S3Operation.DELETE_BUCKET;
            default -> S3Operation.OTHER;
        };
    }

    /** The diagram label: the operation name for Detailed/Summarised; null for Raw. */
    public static String getDiagramLabel(S3OperationInfo op, TrackingVerbosity verbosity) {
        return verbosity == TrackingVerbosity.RAW ? null : op.operation().displayName();
    }

    /** Lower-cased query parameter names (case-insensitive presence checks, matching .NET OrdinalIgnoreCase). */
    private static Set<String> queryKeys(String query) {
        Set<String> keys = new HashSet<>();
        if (query == null || query.isEmpty()) {
            return keys;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            keys.add(name.toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String nullIfEmpty(String s) {
        return isEmpty(s) ? null : s;
    }
}
