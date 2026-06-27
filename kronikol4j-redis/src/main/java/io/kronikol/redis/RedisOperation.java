package io.kronikol.redis;

/**
 * Classified Redis operation types. Java port of the .NET {@code RedisOperation} enum. Each constant carries
 * the .NET {@code ToString()} (PascalCase) display name, used to build the diagram label (e.g. {@code "Get
 * (Hit)"}) byte-identically.
 */
public enum RedisOperation {

    GET("Get"),
    SET("Set"),
    DELETE("Delete"),
    KEY_EXISTS("KeyExists"),
    EXPIRE("Expire"),
    HASH_GET("HashGet"),
    HASH_SET("HashSet"),
    HASH_DELETE("HashDelete"),
    HASH_GET_ALL("HashGetAll"),
    LIST_PUSH("ListPush"),
    LIST_RANGE("ListRange"),
    SET_ADD("SetAdd"),
    SET_MEMBERS("SetMembers"),
    INCREMENT("Increment"),
    DECREMENT("Decrement"),
    PUBLISH("Publish"),
    OTHER("Other");

    private final String displayName;

    RedisOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
