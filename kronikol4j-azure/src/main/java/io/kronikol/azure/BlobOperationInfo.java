package io.kronikol.azure;

/**
 * The result of classifying a Blob Storage request: the operation type, the container name (nullable), and
 * the blob name (nullable). Java port of the .NET {@code BlobOperationInfo} record.
 */
public record BlobOperationInfo(BlobOperation operation, String containerName, String blobName) {
}
