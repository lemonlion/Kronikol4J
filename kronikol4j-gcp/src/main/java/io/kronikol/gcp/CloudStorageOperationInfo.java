package io.kronikol.gcp;

/**
 * The result of classifying a Cloud Storage request: the operation type, the bucket name (nullable), and the
 * object name (nullable). Java port of the .NET {@code CloudStorageOperationInfo} record.
 */
public record CloudStorageOperationInfo(CloudStorageOperation operation, String bucketName, String objectName) {

    public CloudStorageOperationInfo(CloudStorageOperation operation, String bucketName) {
        this(operation, bucketName, null);
    }
}
