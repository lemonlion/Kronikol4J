package io.kronikol.gcp;

/**
 * The result of classifying a Pub/Sub operation: the operation type, the topic name (nullable), the
 * subscription name (nullable), and the message count for batch publishes (nullable). Java port of the .NET
 * {@code PubSubOperationInfo} record.
 */
public record PubSubOperationInfo(PubSubOperation operation, String topicName, String subscriptionName,
                                  Integer messageCount) {
}
