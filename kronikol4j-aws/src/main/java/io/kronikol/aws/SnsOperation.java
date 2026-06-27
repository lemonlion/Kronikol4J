package io.kronikol.aws;

/**
 * Classified Amazon SNS operation types. Java port of the .NET {@code SnsOperation} enum. Each constant
 * carries the AWS operation name (PascalCase), used as the diagram label.
 */
public enum SnsOperation {

    PUBLISH("Publish"),
    PUBLISH_BATCH("PublishBatch"),
    SUBSCRIBE("Subscribe"),
    UNSUBSCRIBE("Unsubscribe"),
    CREATE_TOPIC("CreateTopic"),
    DELETE_TOPIC("DeleteTopic"),
    LIST_TOPICS("ListTopics"),
    LIST_SUBSCRIPTIONS("ListSubscriptions"),
    LIST_SUBSCRIPTIONS_BY_TOPIC("ListSubscriptionsByTopic"),
    GET_TOPIC_ATTRIBUTES("GetTopicAttributes"),
    SET_TOPIC_ATTRIBUTES("SetTopicAttributes"),
    CONFIRM_SUBSCRIPTION("ConfirmSubscription"),
    OTHER("Other");

    private final String displayName;

    SnsOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The AWS operation name (PascalCase) used as the diagram label. */
    public String displayName() {
        return displayName;
    }
}
