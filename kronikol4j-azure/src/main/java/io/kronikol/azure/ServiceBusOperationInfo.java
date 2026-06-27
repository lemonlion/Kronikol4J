package io.kronikol.azure;

/**
 * The result of classifying a Service Bus operation: the operation type, the queue/topic name (nullable),
 * the subscription name (nullable), and the message count for batch operations (nullable). Java port of the
 * .NET {@code ServiceBusOperationInfo} record.
 */
public record ServiceBusOperationInfo(ServiceBusOperation operation, String queueOrTopicName,
                                      String subscriptionName, Integer messageCount) {

    public ServiceBusOperationInfo(ServiceBusOperation operation, String queueOrTopicName, Integer messageCount) {
        this(operation, queueOrTopicName, null, messageCount);
    }
}
