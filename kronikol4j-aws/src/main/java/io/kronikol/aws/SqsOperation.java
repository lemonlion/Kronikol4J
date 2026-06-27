package io.kronikol.aws;

/**
 * Classified Amazon SQS operation types. Java port of the .NET {@code SqsOperation} enum. Each constant
 * carries the AWS operation name (PascalCase), used as the diagram label.
 */
public enum SqsOperation {

    SEND_MESSAGE("SendMessage"),
    SEND_MESSAGE_BATCH("SendMessageBatch"),
    RECEIVE_MESSAGE("ReceiveMessage"),
    DELETE_MESSAGE("DeleteMessage"),
    DELETE_MESSAGE_BATCH("DeleteMessageBatch"),
    CHANGE_MESSAGE_VISIBILITY("ChangeMessageVisibility"),
    CHANGE_MESSAGE_VISIBILITY_BATCH("ChangeMessageVisibilityBatch"),
    CREATE_QUEUE("CreateQueue"),
    DELETE_QUEUE("DeleteQueue"),
    GET_QUEUE_URL("GetQueueUrl"),
    GET_QUEUE_ATTRIBUTES("GetQueueAttributes"),
    SET_QUEUE_ATTRIBUTES("SetQueueAttributes"),
    PURGE_QUEUE("PurgeQueue"),
    LIST_QUEUES("ListQueues"),
    OTHER("Other");

    private final String displayName;

    SqsOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The AWS operation name (PascalCase) used as the diagram label. */
    public String displayName() {
        return displayName;
    }
}
