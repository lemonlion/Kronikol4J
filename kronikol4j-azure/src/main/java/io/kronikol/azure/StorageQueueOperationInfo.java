package io.kronikol.azure;

/**
 * The result of classifying an Azure Storage Queues operation. Java port of the .NET
 * {@code StorageQueueOperationInfo}.
 *
 * @param operation the classified operation
 * @param queueName the queue name (or {@code null})
 * @param messageId the message id, for per-message operations (or {@code null})
 */
public record StorageQueueOperationInfo(StorageQueueOperation operation, String queueName, String messageId) {

    public StorageQueueOperationInfo(StorageQueueOperation operation, String queueName) {
        this(operation, queueName, null);
    }
}
