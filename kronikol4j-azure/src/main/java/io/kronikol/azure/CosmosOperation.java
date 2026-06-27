package io.kronikol.azure;

/**
 * Classified Azure Cosmos DB operation types. Java port of the .NET {@code CosmosOperation} enum. Each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used as the diagram label.
 */
public enum CosmosOperation {

    CREATE("Create"),
    READ("Read"),
    REPLACE("Replace"),
    PATCH("Patch"),
    DELETE("Delete"),
    UPSERT("Upsert"),
    QUERY("Query"),
    LIST("List"),
    EXEC_STORED_PROC("ExecStoredProc"),
    BATCH("Batch"),
    OTHER("Other");

    private final String displayName;

    CosmosOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
