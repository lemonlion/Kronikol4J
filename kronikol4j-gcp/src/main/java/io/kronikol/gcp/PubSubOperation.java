package io.kronikol.gcp;

/**
 * Classified Google Cloud Pub/Sub operation types. Java port of the .NET {@code PubSubOperation} enum. Each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used as the diagram-label fallback.
 */
public enum PubSubOperation {

    PUBLISH("Publish"),
    PUBLISH_BATCH("PublishBatch"),
    PULL("Pull"),
    ACKNOWLEDGE("Acknowledge"),
    MODIFY_ACK_DEADLINE("ModifyAckDeadline"),
    RECEIVE("Receive"),
    START_SUBSCRIBER("StartSubscriber"),
    STOP_SUBSCRIBER("StopSubscriber"),
    OTHER("Other");

    private final String displayName;

    PubSubOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
