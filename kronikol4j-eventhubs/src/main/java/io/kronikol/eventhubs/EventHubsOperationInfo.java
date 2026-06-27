package io.kronikol.eventhubs;

/**
 * The result of classifying an Event Hubs operation. Java port of the .NET {@code EventHubsOperationInfo}.
 *
 * @param operation    the classified operation
 * @param eventHubName the event hub name (or {@code null})
 * @param partitionId  the partition id (or {@code null})
 * @param eventCount   the event count for batch sends (or {@code null})
 */
public record EventHubsOperationInfo(EventHubsOperation operation, String eventHubName, String partitionId,
                                     Integer eventCount) {

    public EventHubsOperationInfo(EventHubsOperation operation, String eventHubName) {
        this(operation, eventHubName, null, null);
    }
}
