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

    public static String mongo(String serviceName, String documentId) {
        return "mongo:" + serviceName + ":" + documentId;
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
