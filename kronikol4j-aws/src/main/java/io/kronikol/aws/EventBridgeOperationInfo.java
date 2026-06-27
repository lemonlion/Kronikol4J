package io.kronikol.aws;

/**
 * The result of classifying an EventBridge operation. Java port of the .NET {@code EventBridgeOperationInfo}.
 *
 * @param operation    the classified operation
 * @param eventBusName the event bus name (or {@code null})
 * @param ruleName     the rule name, for rule operations (or {@code null})
 * @param detailType   the first entry's {@code DetailType}, for {@code PutEvents} (or {@code null})
 * @param source       the first entry's {@code Source}, for {@code PutEvents} (or {@code null})
 * @param entryCount   the number of entries, for {@code PutEvents} (or {@code null})
 */
public record EventBridgeOperationInfo(EventBridgeOperation operation, String eventBusName, String ruleName,
                                       String detailType, String source, Integer entryCount) {

    public EventBridgeOperationInfo(EventBridgeOperation operation) {
        this(operation, null, null, null, null, null);
    }
}
