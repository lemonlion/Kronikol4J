package io.kronikol.mongodb;

/**
 * Classified MongoDB Atlas Data API operation types. Java port of the .NET {@code AtlasDataApiOperation}
 * enum; each constant carries the .NET {@code ToString()} (PascalCase) display name used in diagram labels.
 */
public enum AtlasDataApiOperation {

    FIND_ONE("FindOne"),
    FIND("Find"),
    INSERT_ONE("InsertOne"),
    INSERT_MANY("InsertMany"),
    UPDATE_ONE("UpdateOne"),
    UPDATE_MANY("UpdateMany"),
    DELETE_ONE("DeleteOne"),
    DELETE_MANY("DeleteMany"),
    REPLACE_ONE("ReplaceOne"),
    AGGREGATE("Aggregate"),
    OTHER("Other");

    private final String displayName;

    AtlasDataApiOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
