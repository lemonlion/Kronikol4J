package io.kronikol.aws;

/**
 * Classified Amazon EventBridge operation types. Java port of the .NET {@code EventBridgeOperation} enum;
 * each constant carries the .NET {@code ToString()} (PascalCase) display name used in diagram labels.
 */
public enum EventBridgeOperation {

    // Events
    PUT_EVENTS("PutEvents"),
    PUT_PARTNER_EVENTS("PutPartnerEvents"),
    TEST_EVENT_PATTERN("TestEventPattern"),

    // Rules
    PUT_RULE("PutRule"),
    DELETE_RULE("DeleteRule"),
    DESCRIBE_RULE("DescribeRule"),
    ENABLE_RULE("EnableRule"),
    DISABLE_RULE("DisableRule"),
    LIST_RULES("ListRules"),

    // Targets
    PUT_TARGETS("PutTargets"),
    REMOVE_TARGETS("RemoveTargets"),
    LIST_TARGETS_BY_RULE("ListTargetsByRule"),

    // Event Buses
    CREATE_EVENT_BUS("CreateEventBus"),
    DELETE_EVENT_BUS("DeleteEventBus"),
    DESCRIBE_EVENT_BUS("DescribeEventBus"),
    LIST_EVENT_BUSES("ListEventBuses"),

    // Archives
    CREATE_ARCHIVE("CreateArchive"),
    DELETE_ARCHIVE("DeleteArchive"),
    DESCRIBE_ARCHIVE("DescribeArchive"),
    LIST_ARCHIVES("ListArchives"),

    // Replays
    START_REPLAY("StartReplay"),
    DESCRIBE_REPLAY("DescribeReplay"),
    LIST_REPLAYS("ListReplays"),

    // API Destinations & Connections
    CREATE_API_DESTINATION("CreateApiDestination"),
    CREATE_CONNECTION("CreateConnection"),

    // Tags
    TAG_RESOURCE("TagResource"),
    UNTAG_RESOURCE("UntagResource"),
    LIST_TAGS_FOR_RESOURCE("ListTagsForResource"),

    OTHER("Other");

    private final String displayName;

    EventBridgeOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
