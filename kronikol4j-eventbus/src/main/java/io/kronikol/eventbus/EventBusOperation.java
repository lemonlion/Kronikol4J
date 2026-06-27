package io.kronikol.eventbus;

/**
 * Classified message-bus operation types. Java port of the .NET {@code MassTransitOperation} enum; each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used in diagram labels.
 */
public enum EventBusOperation {

    SEND("Send"),
    PUBLISH("Publish"),
    CONSUME("Consume"),
    SEND_FAULT("SendFault"),
    PUBLISH_FAULT("PublishFault"),
    CONSUME_FAULT("ConsumeFault"),
    OTHER("Other");

    private final String displayName;

    EventBusOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
