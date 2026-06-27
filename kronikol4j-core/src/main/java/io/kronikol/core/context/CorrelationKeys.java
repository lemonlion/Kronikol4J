package io.kronikol.core.context;

/**
 * Standard key formats for {@link TestCorrelationStore}. Values mirror the .NET {@code CorrelationKeys}.
 */
public final class CorrelationKeys {

    private CorrelationKeys() {
    }

    public static String cosmos(String serviceName, String documentId) {
        return "cosmos:" + serviceName + ":" + documentId;
    }

    /** Cosmos DB document key including the partition key. */
    public static String cosmos(String serviceName, String partitionKey, String documentId) {
        return "cosmos:" + serviceName + ":" + partitionKey + ":" + documentId;
    }

    public static String mongo(String serviceName, String documentId) {
        return "mongo:" + serviceName + ":" + documentId;
    }

    /** Azure Event Hubs event key. */
    public static String eventHubs(String serviceName, String eventId) {
        return "eventhubs:" + serviceName + ":" + eventId;
    }

    /** Google Pub/Sub message key. */
    public static String pubSub(String serviceName, String messageId) {
        return "pubsub:" + serviceName + ":" + messageId;
    }

    /** AWS SQS message key. */
    public static String sqs(String serviceName, String messageId) {
        return "sqs:" + serviceName + ":" + messageId;
    }

    /** AWS SNS message key. */
    public static String sns(String serviceName, String messageId) {
        return "sns:" + serviceName + ":" + messageId;
    }

    /** Azure Storage Queue message key. */
    public static String storageQueue(String serviceName, String messageId) {
        return "storagequeue:" + serviceName + ":" + messageId;
    }

    public static String kafka(String serviceName, String messageKey) {
        return "kafka:" + serviceName + ":" + messageKey;
    }

    public static String serviceBus(String serviceName, String messageId) {
        return "servicebus:" + serviceName + ":" + messageId;
    }

    public static String custom(String prefix, String serviceName, String itemId) {
        return prefix + ":" + serviceName + ":" + itemId;
    }
}
