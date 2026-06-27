package io.kronikol.azure;

/**
 * Classified Azure Storage Queues operation types. Java port of the .NET {@code StorageQueueOperation} enum;
 * each constant carries the .NET {@code ToString()} (PascalCase) display name used in diagram labels.
 */
public enum StorageQueueOperation {

    SEND_MESSAGE("SendMessage"),
    RECEIVE_MESSAGES("ReceiveMessages"),
    PEEK_MESSAGES("PeekMessages"),
    DELETE_MESSAGE("DeleteMessage"),
    UPDATE_MESSAGE("UpdateMessage"),
    CLEAR_MESSAGES("ClearMessages"),
    CREATE_QUEUE("CreateQueue"),
    DELETE_QUEUE("DeleteQueue"),
    GET_PROPERTIES("GetProperties"),
    SET_METADATA("SetMetadata"),
    LIST_QUEUES("ListQueues"),
    OTHER("Other");

    private final String displayName;

    StorageQueueOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
