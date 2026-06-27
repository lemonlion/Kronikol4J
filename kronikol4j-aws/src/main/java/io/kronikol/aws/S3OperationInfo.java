package io.kronikol.aws;

/**
 * The result of classifying an S3 request: the operation type, the bucket name (nullable), and the object key
 * (nullable). Java port of the .NET {@code S3OperationInfo} record.
 */
public record S3OperationInfo(S3Operation operation, String bucketName, String keyName) {

    public S3OperationInfo(S3Operation operation, String bucketName) {
        this(operation, bucketName, null);
    }
}
