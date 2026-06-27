package io.kronikol.eventhubs;

/**
 * Classified Azure Event Hubs operation types. Java port of the .NET {@code EventHubsOperation} enum; each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used in diagram labels.
 */
public enum EventHubsOperation {

    SEND("Send"),
    SEND_BATCH("SendBatch"),
    CREATE_BATCH("CreateBatch"),
    READ_EVENTS("ReadEvents"),
    READ_EVENTS_FROM_PARTITION("ReadEventsFromPartition"),
    GET_PARTITION_IDS("GetPartitionIds"),
    GET_EVENT_HUB_PROPERTIES("GetEventHubProperties"),
    GET_PARTITION_PROPERTIES("GetPartitionProperties"),
    START_PROCESSING("StartProcessing"),
    STOP_PROCESSING("StopProcessing"),
    PROCESS_EVENT("ProcessEvent"),
    OTHER("Other");

    private final String displayName;

    EventHubsOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
