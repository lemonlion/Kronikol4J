package io.kronikol.gcp;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Google Cloud Storage requests into typed operations from the HTTP method, the
 * {@code /storage/v1/b/{bucket}/o/{object}} path (and the {@code /upload/} variant), the {@code alt=media}
 * query flag, and {@code /copyTo/} / {@code /compose} sub-paths. Java port of the .NET
 * {@code CloudStorageOperationClassifier}. Pure logic — no Cloud Storage SDK dependency.
 */
public final class CloudStorageOperationClassifier {

    private static final Pattern GCS_PATH = Pattern.compile(
        "/(?:upload/)?storage/v1/b/(?<bucket>[^/?]+)(?:/o(?:/(?<object>[^/?]+))?)?");
    private static final Pattern BUCKET_ONLY = Pattern.compile("/storage/v1/b(?:/(?<bucket>[^/?]+))?$");

    private CloudStorageOperationClassifier() {
    }

    /** Classifies a Cloud Storage request from its HTTP method and URI. */
    public static CloudStorageOperationInfo classify(String httpMethod, URI uri) {
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        // Raw path keeps %-encoding (matching .NET AbsolutePath) so an encoded object name like
        // "folder%2Ffile.txt" stays one path segment rather than being split on the decoded '/'.
        String path = uri == null || uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri == null ? null : uri.getQuery();
        boolean isUpload = path.startsWith("/upload/");

        Matcher m = GCS_PATH.matcher(path);
        if (!m.find()) {
            return classifyBucketOnly(method, path);
        }

        String bucket = m.group("bucket");
        // No /o after the bucket → a bucket-level operation.
        if (!m.group().contains("/o")) {
            return switch (method) {
                case "GET" -> new CloudStorageOperationInfo(CloudStorageOperation.GET_BUCKET, bucket);
                case "DELETE" -> new CloudStorageOperationInfo(CloudStorageOperation.DELETE_BUCKET, bucket);
                default -> new CloudStorageOperationInfo(CloudStorageOperation.OTHER, bucket);
            };
        }

        String objectGroup = m.group("object");
        String object = objectGroup != null && !objectGroup.isEmpty() ? percentDecode(objectGroup) : null;
        boolean hasObject = object != null;

        if (path.contains("/copyTo/")) {
            return new CloudStorageOperationInfo(CloudStorageOperation.COPY, bucket, object);
        }
        if (path.contains("/compose")) {
            return new CloudStorageOperationInfo(CloudStorageOperation.COMPOSE, bucket, object);
        }

        boolean altMedia = query != null && query.contains("alt=media");
        CloudStorageOperation operation;
        if (method.equals("POST") && isUpload) {
            operation = CloudStorageOperation.UPLOAD;
        } else if (method.equals("PUT") && hasObject) {
            operation = CloudStorageOperation.UPLOAD;
        } else if (method.equals("GET") && hasObject && altMedia) {
            operation = CloudStorageOperation.DOWNLOAD;
        } else if (method.equals("GET") && hasObject) {
            operation = CloudStorageOperation.GET_METADATA;
        } else if (method.equals("DELETE") && hasObject) {
            operation = CloudStorageOperation.DELETE;
        } else if (method.equals("PATCH") && hasObject) {
            operation = CloudStorageOperation.UPDATE_METADATA;
        } else if (method.equals("GET")) { // no object
            operation = CloudStorageOperation.LIST_OBJECTS;
        } else {
            operation = CloudStorageOperation.OTHER;
        }
        return new CloudStorageOperationInfo(operation, bucket, object);
    }

    private static CloudStorageOperationInfo classifyBucketOnly(String method, String path) {
        Matcher b = BUCKET_ONLY.matcher(path);
        if (!b.find()) {
            return new CloudStorageOperationInfo(CloudStorageOperation.OTHER, null);
        }
        String bucket = b.group("bucket");
        boolean hasBucket = bucket != null && !bucket.isEmpty();
        if (method.equals("GET") && hasBucket) {
            return new CloudStorageOperationInfo(CloudStorageOperation.GET_BUCKET, bucket);
        }
        if (method.equals("DELETE") && hasBucket) {
            return new CloudStorageOperationInfo(CloudStorageOperation.DELETE_BUCKET, bucket);
        }
        if (method.equals("POST")) {
            return new CloudStorageOperationInfo(CloudStorageOperation.CREATE_BUCKET, null);
        }
        if (method.equals("GET")) { // no bucket
            return new CloudStorageOperationInfo(CloudStorageOperation.LIST_BUCKETS, null);
        }
        return new CloudStorageOperationInfo(CloudStorageOperation.OTHER, null);
    }

    /** The diagram label per verbosity (Detailed appends the bucket/object target). */
    public static String getDiagramLabel(CloudStorageOperationInfo op, TrackingVerbosity verbosity) {
        if (verbosity == TrackingVerbosity.DETAILED) {
            if (op.objectName() != null) {
                return op.operation().displayName() + " → " + op.bucketName() + "/" + op.objectName();
            }
            return op.bucketName() != null
                ? op.operation().displayName() + " → " + op.bucketName()
                : op.operation().displayName();
        }
        return op.operation().displayName(); // Summarised + Raw
    }

    /** Percent-decodes a URL path segment (matching .NET {@code Uri.UnescapeDataString}; {@code +} is literal). */
    private static String percentDecode(String s) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                bytes.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                bytes.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
