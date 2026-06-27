package io.kronikol.eventhubs;

import io.kronikol.core.tracking.TrackingVerbosity;

/**
 * Classifies Azure Event Hubs operations from the SDK method name and labels them for the diagram. Java port
 * of the .NET {@code EventHubsOperationClassifier} (pure logic — no Event Hubs SDK dependency). A
 * {@code SendAsync} with more than one event folds onto {@link EventHubsOperation#SEND_BATCH}.
 */
public final class EventHubsOperationClassifier {

    private EventHubsOperationClassifier() {
    }

    /** Classifies {@code methodName} (e.g. {@code "SendAsync"}/{@code "ReadEventsAsync"}) into an operation. */
    public static EventHubsOperationInfo classify(String methodName, String eventHubName, String partitionId,
                                                  Integer eventCount) {
        EventHubsOperation operation;
        if ("SendAsync".equals(methodName)) {
            operation = eventCount != null && eventCount > 1 ? EventHubsOperation.SEND_BATCH
                : EventHubsOperation.SEND;
        } else {
            operation = switch (methodName == null ? "" : methodName) {
                case "CreateBatchAsync" -> EventHubsOperation.CREATE_BATCH;
                case "ReadEventsAsync" -> EventHubsOperation.READ_EVENTS;
                case "ReadEventsFromPartitionAsync" -> EventHubsOperation.READ_EVENTS_FROM_PARTITION;
                case "GetPartitionIdsAsync" -> EventHubsOperation.GET_PARTITION_IDS;
                case "GetEventHubPropertiesAsync" -> EventHubsOperation.GET_EVENT_HUB_PROPERTIES;
                case "GetPartitionPropertiesAsync" -> EventHubsOperation.GET_PARTITION_PROPERTIES;
                case "StartProcessingAsync" -> EventHubsOperation.START_PROCESSING;
                case "StopProcessingAsync" -> EventHubsOperation.STOP_PROCESSING;
                case "ProcessEvent" -> EventHubsOperation.PROCESS_EVENT;
                default -> EventHubsOperation.OTHER;
            };
        }
        return new EventHubsOperationInfo(operation, eventHubName, partitionId, eventCount);
    }

    /** The diagram label for an operation at the given verbosity (matches the .NET {@code GetDiagramLabel}). */
    public static String getDiagramLabel(EventHubsOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.operation().displayName() + " hub=" + op.eventHubName()
                + " partition=" + op.partitionId() + " count=" + op.eventCount();
            case DETAILED -> switch (op.operation()) {
                case SEND -> "Send → " + op.eventHubName();
                case SEND_BATCH -> "Send (×" + op.eventCount() + ") → " + op.eventHubName();
                case READ_EVENTS -> "Read ← " + op.eventHubName();
                case READ_EVENTS_FROM_PARTITION -> "Read ← " + op.eventHubName() + "[" + op.partitionId() + "]";
                case PROCESS_EVENT -> "Process ← " + op.eventHubName();
                default -> op.operation().displayName();
            };
            case SUMMARISED -> switch (op.operation()) {
                case SEND_BATCH -> "Send";
                case READ_EVENTS_FROM_PARTITION -> "Read";
                default -> op.operation().displayName();
            };
        };
    }
}
