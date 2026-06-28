package io.kronikol.messaging;

/**
 * The result of classifying a Kafka operation: the operation type plus optional metadata (topic,
 * partition, offset). Java port of the .NET {@code KafkaOperationInfo} record; {@code partition}/
 * {@code offset} are nullable (the .NET {@code int?}/{@code long?}).
 */
public record KafkaOperationInfo(KafkaOperation operation, String topic, Integer partition, Long offset) {

    public KafkaOperationInfo(KafkaOperation operation) {
        this(operation, null, null, null);
    }

    public KafkaOperationInfo(KafkaOperation operation, String topic) {
        this(operation, topic, null, null);
    }
}
